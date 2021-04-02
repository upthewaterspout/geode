package org.apache.geode.redis.internal;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Supplier;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;

import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.distributed.internal.ServerLocation;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.internal.cache.CountingDataInputStream;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.cache.client.protocol.ClientProtocolProcessor;
import org.apache.geode.internal.cache.tier.Acceptor;
import org.apache.geode.internal.cache.tier.CachedRegionHelper;
import org.apache.geode.internal.cache.tier.sockets.CacheServerStats;
import org.apache.geode.internal.cache.tier.sockets.ClientProxyMembershipID;
import org.apache.geode.internal.cache.tier.sockets.ServerConnection;
import org.apache.geode.internal.security.SecurityService;
import org.apache.geode.redis.internal.executor.RedisResponse;
import org.apache.geode.redis.internal.netty.ByteToCommandReader;
import org.apache.geode.redis.internal.netty.Command;
import org.apache.geode.redis.internal.netty.ExecutionHandlerContext;
import org.apache.geode.redis.internal.netty.RedisCommandParserException;
import org.apache.geode.redis.internal.pubsub.PubSub;
import org.apache.geode.redis.internal.statistics.RedisStats;

public class RedisServerConnection extends ServerConnection {
  private final Socket socket;
  private final InternalCache cache;
  private final ByteToCommandReader decoder;
  private final RedisStats redisStats;
  private final CountingDataInputStream input;
  private final ByteBuf responseBuf;
  private final ExecutionHandlerContext executionHandlerContext;
  private final ClientProtocolProcessor clientProtocolProcessor;
  private final Supplier<Boolean> allowUnsupportedSupplier;
  private ClientProxyMembershipID clientProxyMembershipID;

  public RedisServerConnection(final Socket socket, final InternalCache internalCache,
      final CachedRegionHelper cachedRegionHelper,
      final CacheServerStats stats,
      final int hsTimeout, final int socketBufferSize,
      final String communicationModeStr,
      final byte communicationMode, final Acceptor acceptor,
      final ClientProtocolProcessor clientProtocolProcessor,
      final SecurityService securityService,
      RedisStats redisStats,
      RegionProvider regionProvider,
      PubSub pubSub,
      Supplier<Boolean> allowUnsupportedSupplier,
      Runnable shutdownInvoker)
      throws IOException {
    super(socket, internalCache, cachedRegionHelper, stats, hsTimeout, socketBufferSize,
        communicationModeStr, communicationMode, acceptor, securityService);
    this.clientProtocolProcessor = clientProtocolProcessor;
    this.allowUnsupportedSupplier = allowUnsupportedSupplier;
    this.executionHandlerContext = new ExecutionHandlerContext(null, regionProvider, pubSub, null,
        shutdownInvoker, redisStats, socket.getPort());
    this.socket = socket;
    this.cache = internalCache;
    this.redisStats = redisStats;
    this.input = new CountingDataInputStream(socket.getInputStream(), -1);
    decoder = new ByteToCommandReader(redisStats);
    setClientProxyMembershipId();

    // TODO - probably ought to release this. But really shouldn't be using netty anyway
    // this is just for testing
    // Using a heap buffer because our socket is has no channel so we can't use a direct byte
    // buffer to anyway.
    responseBuf = new UnpooledByteBufAllocator(false).heapBuffer();
  }

  private void setClientProxyMembershipId() {
    ServerLocation serverLocation = new ServerLocation(
        ((InetSocketAddress) getSocket().getRemoteSocketAddress()).getHostString(),
        getSocketPort());
    DistributedMember distributedMember = new InternalDistributedMember(serverLocation);
    // no handshake for new client protocol.
    clientProxyMembershipID = new ClientProxyMembershipID(distributedMember);
  }

  @Override
  protected boolean doHandShake(byte epType, int qSize) {
    // nothing to do here for redis
    return true;
  }

  @Override
  protected void doOneMessage() {
    try {
      Command command = decoder.parse(this.input);
      // System.out.println("Parsed " + command);
      executeCommand(command);
    } catch (RedisCommandParserException | IOException e) {
      if (!socket.isClosed()) {
        logger.warn(e);
      }
      setFlagProcessMessagesAsFalse();
      setClientDisconnectedException(e);
    } finally {
      getAcceptor().getClientHealthMonitor().receivedPing(clientProxyMembershipID);
    }


  }

  @Override
  public boolean cleanup() {
    try {
      socket.close();
    } catch (IOException e) {
      // no nothing
    }
    return true;
  }

  public void writeToChannel(RedisResponse response) throws IOException {
    ByteBuf bufResponse = response.encode(responseBuf);
    int oldWriteIndex = bufResponse.writerIndex();
    socket.getOutputStream().write(bufResponse.array(), bufResponse.readerIndex(),
        bufResponse.readableBytes());
    socket.getOutputStream().flush();
    // System.out.println("Wrote " + bufResponse.toString(StandardCharsets.US_ASCII));
    // Dang, we're not using a NIO socket??
    // socket.getChannel().write(bufResponse.nioBuffer());
    responseBuf.clear();
    response.afterWrite();
  }


  // From ExecutionHandlerContext
  private void executeCommand(Command command) throws IOException {
    try {
      if (logger.isDebugEnabled()) {
        logger.debug("Executing Redis command: {} - {}", command, socket.getRemoteSocketAddress());
      }

      if (command.isUnknown()) {
        writeToChannel(executeAndGetResponse(command));
        return;
      }

      if (!executionHandlerContext.isAuthenticated()) {
        writeToChannel(RedisResponse.customError(RedisConstants.ERROR_NOT_AUTH));
        return;
      }

      if (command.isUnsupported() && !allowUnsupportedCommands()) {
        writeToChannel(
            RedisResponse
                .error(command.getCommandType() + RedisConstants.ERROR_UNSUPPORTED_COMMAND));
        return;
      }

      if (command.isUnimplemented()) {
        logger.info("Failed " + command.getCommandType() + " because it is not implemented.");
        writeToChannel(RedisResponse.error(command.getCommandType() + " is not implemented."));
        return;
      }

      // if (!getPubSub().findSubscriptionNames(getClient()).isEmpty()) {
      // if (!command.getCommandType().isAllowedWhileSubscribed()) {
      // writeToChannel(RedisResponse
      // .error("only (P)SUBSCRIBE / (P)UNSUBSCRIBE / PING / QUIT allowed in this context"));
      // }
      // }

      final long start = redisStats.startCommand();
      try {
        writeToChannel(executeAndGetResponse(command));
      } finally {
        redisStats.endCommand(command.getCommandType(), start);
      }

      if (command.isOfType(RedisCommandType.QUIT)) {
        setFlagProcessMessagesAsFalse();
        setClientDisconnectCleanly();
        this.cleanup();
      }
    } catch (Exception e) {
      logger.warn("Execution of Redis command {} failed: {}", command, e);
      throw e;
    }
  }

  private RedisResponse executeAndGetResponse(Command command) {
    try {
      return command.execute(executionHandlerContext);
    } catch (Exception e) {
      try {
        return executionHandlerContext.getExceptionResponse(null, e);
      } catch (Exception ignore) {
        logger.warn("Error handling exception", ignore);
        throw e;
      }
    }
  }

  private boolean allowUnsupportedCommands() {
    return this.allowUnsupportedSupplier.get();
  }

}
