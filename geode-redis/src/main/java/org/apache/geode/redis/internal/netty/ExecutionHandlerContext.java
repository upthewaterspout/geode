package org.apache.geode.redis.internal.netty;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import org.apache.logging.log4j.Logger;

import org.apache.geode.ForcedDisconnectException;
import org.apache.geode.cache.CacheClosedException;
import org.apache.geode.cache.execute.FunctionException;
import org.apache.geode.cache.execute.FunctionInvocationTargetException;
import org.apache.geode.distributed.DistributedSystemDisconnectedException;
import org.apache.geode.logging.internal.log4j.api.LogService;
import org.apache.geode.redis.internal.GeodeRedisServer;
import org.apache.geode.redis.internal.ParameterRequirements.RedisParametersMismatchException;
import org.apache.geode.redis.internal.RedisConstants;
import org.apache.geode.redis.internal.RegionProvider;
import org.apache.geode.redis.internal.data.RedisDataTypeMismatchException;
import org.apache.geode.redis.internal.executor.CommandFunction;
import org.apache.geode.redis.internal.executor.RedisResponse;
import org.apache.geode.redis.internal.executor.hash.RedisHashCommands;
import org.apache.geode.redis.internal.executor.hash.RedisHashCommandsFunctionInvoker;
import org.apache.geode.redis.internal.pubsub.PubSub;
import org.apache.geode.redis.internal.statistics.RedisStats;

public class ExecutionHandlerContext extends ChannelInboundHandlerAdapter {
  protected static final Logger logger = LogService.getLogger();
  protected static final Command TERMINATE_COMMAND = new Command();
  protected final Client client;
  protected final RegionProvider regionProvider;
  protected final PubSub pubsub;
  protected final byte[] authPassword;
  protected final Runnable shutdownInvoker;
  protected final RedisStats redisStats;
  protected final RedisHashCommandsFunctionInvoker hashCommands;
  protected final int serverPort;
  protected BigInteger scanCursor;
  protected BigInteger sscanCursor;
  protected BigInteger hscanCursor;
  protected boolean isAuthenticated;
  private CountDownLatch eventLoopSwitched;

  public ExecutionHandlerContext(
      Channel channel, RegionProvider regionProvider, PubSub pubsub, byte[] password,
      Runnable shutdownInvoker, RedisStats redisStats, int serverPort) {
    this.client = new Client(channel);
    this.regionProvider = regionProvider;
    this.pubsub = pubsub;
    this.authPassword = password;
    this.shutdownInvoker = shutdownInvoker;
    this.redisStats = redisStats;
    this.hashCommands = new RedisHashCommandsFunctionInvoker(getRegionProvider().getDataRegion());
    this.scanCursor = new BigInteger("0");
    this.sscanCursor = new BigInteger("0");
    this.hscanCursor = new BigInteger("0");
    this.serverPort = serverPort;
    this.isAuthenticated = password == null;
  }

  /**
   * Gets the provider of Regions
   */
  public RegionProvider getRegionProvider() {
    return regionProvider;
  }

  /**
   * Get the authentication password, this will be same server wide. It is exposed here as opposed
   * to {@link GeodeRedisServer}.
   */
  public byte[] getAuthPassword() {
    return this.authPassword;
  }

  /**
   * Checker if user has authenticated themselves
   *
   * @return True if no authentication required or authentication complete, false otherwise
   */
  public boolean isAuthenticated() {
    return this.isAuthenticated;
  }

  /**
   * Lets this context know the authentication is complete
   */
  public void setAuthenticationVerified() {
    this.isAuthenticated = true;
  }

  public int getServerPort() {
    return serverPort;
  }

  public Client getClient() {
    return client;
  }

  public void shutdown() {
    shutdownInvoker.run();
  }

  public PubSub getPubSub() {
    return pubsub;
  }

  public RedisStats getRedisStats() {
    return redisStats;
  }

  public BigInteger getScanCursor() {
    return scanCursor;
  }

  public void setScanCursor(BigInteger scanCursor) {
    this.scanCursor = scanCursor;
  }

  public BigInteger getSscanCursor() {
    return sscanCursor;
  }

  public void setSscanCursor(BigInteger sscanCursor) {
    this.sscanCursor = sscanCursor;
  }

  public BigInteger getHscanCursor() {
    return hscanCursor;
  }

  public void setHscanCursor(BigInteger hscanCursor) {
    this.hscanCursor = hscanCursor;
  }

  /**
   * This method and {@link #eventLoopReady()} are relevant for pubsub related commands which need
   * to return responses on a different EventLoopGroup. We need to ensure that the EventLoopGroup
   * switch has occurred before subsequent commands are executed.
   */
  public CountDownLatch getOrCreateEventLoopLatch() {
    if (eventLoopSwitched != null) {
      return eventLoopSwitched;
    }

    eventLoopSwitched = new CountDownLatch(1);
    return eventLoopSwitched;
  }

  /**
   * Signals that we have successfully switched over to a new EventLoopGroup.
   */
  public void eventLoopReady() {
    if (eventLoopSwitched == null) {
      return;
    }

    try {
      eventLoopSwitched.await();
    } catch (InterruptedException e) {
      logger.info("Event loop interrupted", e);
    }
  }

  public RedisHashCommands getRedisHashCommands() {
    return hashCommands;
  }

  public RedisResponse getExceptionResponse(ChannelHandlerContext ctx, Throwable cause)
      throws Exception {
    RedisResponse response;
    if (cause instanceof IOException) {
      channelInactive(ctx);
      return null;
    }

    if (cause instanceof FunctionException
        && !(cause instanceof FunctionInvocationTargetException)) {
      Throwable th = CommandFunction.getInitialCause((FunctionException) cause);
      if (th != null) {
        cause = th;
      }
    }

    if (cause instanceof NumberFormatException) {
      response = RedisResponse.error(cause.getMessage());
    } else if (cause instanceof ArithmeticException) {
      response = RedisResponse.error(cause.getMessage());
    } else if (cause instanceof RedisDataTypeMismatchException) {
      response = RedisResponse.wrongType(cause.getMessage());
    } else if (cause instanceof DecoderException
        && cause.getCause() instanceof RedisCommandParserException) {
      response = RedisResponse.error(RedisConstants.PARSING_EXCEPTION_MESSAGE);

    } else if (cause instanceof InterruptedException || cause instanceof CacheClosedException) {
      response = RedisResponse.error(RedisConstants.SERVER_ERROR_SHUTDOWN);
    } else if (cause instanceof IllegalStateException
        || cause instanceof RedisParametersMismatchException) {
      response = RedisResponse.error(cause.getMessage());
    } else if (cause instanceof FunctionInvocationTargetException
        || cause instanceof DistributedSystemDisconnectedException
        || cause instanceof ForcedDisconnectException) {
      // This indicates a member departed or got disconnected
      logger.warn(
          "Closing client connection because one of the servers doing this operation departed.");
      channelInactive(ctx);
      response = null;
    } else {
      if (logger.isErrorEnabled()) {
        logger.error("GeodeRedisServer-Unexpected error handler for " + ctx.channel(), cause);
      }
      response = RedisResponse.error(RedisConstants.SERVER_ERROR_MESSAGE);
    }

    return response;
  }
}
