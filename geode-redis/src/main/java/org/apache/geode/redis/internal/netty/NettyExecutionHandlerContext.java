/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.apache.geode.redis.internal.netty;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;

import org.apache.geode.redis.internal.RedisCommandType;
import org.apache.geode.redis.internal.RedisConstants;
import org.apache.geode.redis.internal.RegionProvider;
import org.apache.geode.redis.internal.executor.RedisResponse;
import org.apache.geode.redis.internal.pubsub.PubSub;
import org.apache.geode.redis.internal.statistics.RedisStats;

/**
 * This class extends {@link ChannelInboundHandlerAdapter} from Netty and it is the last part of the
 * channel pipeline. The {@link ByteToCommandDecoder} forwards a {@link Command} to this class which
 * executes it and sends the result back to the client. Additionally, all exception handling is done
 * by this class.
 * <p>
 * Besides being part of Netty's pipeline, this class also serves as a context to the execution of a
 * command. It provides access to the {@link RegionProvider} and anything else an executing {@link
 * Command} may need.
 */
public class NettyExecutionHandlerContext extends ExecutionHandlerContext {

  private final Channel channel;
  private final ByteBufAllocator byteBufAllocator;
  private final Supplier<Boolean> allowUnsupportedSupplier;
  private final EventLoopGroup subscriberGroup;
  private final AtomicBoolean channelInactive = new AtomicBoolean();
  private final int MAX_QUEUED_COMMANDS =
      Integer.getInteger("geode.redis.commandQueueSize", 1000);
  private final LinkedBlockingQueue<Command> commandQueue =
      new LinkedBlockingQueue<>(MAX_QUEUED_COMMANDS);

  /**
   * Default constructor for execution contexts.
   *
   * @param channel Channel used by this context, should be one to one
   * @param password Authentication password for each context, can be null
   */
  public NettyExecutionHandlerContext(Channel channel,
      RegionProvider regionProvider,
      PubSub pubsub,
      Supplier<Boolean> allowUnsupportedSupplier,
      Runnable shutdownInvoker,
      RedisStats redisStats,
      ExecutorService backgroundExecutor,
      EventLoopGroup subscriberGroup,
      byte[] password,
      int serverPort) {
    super(channel, regionProvider, pubsub, password, shutdownInvoker, redisStats, serverPort);
    this.channel = channel;
    this.allowUnsupportedSupplier = allowUnsupportedSupplier;
    this.subscriberGroup = subscriberGroup;
    this.byteBufAllocator = this.channel.alloc();
    redisStats.addClient();

    // TODO - this really should just be a cache wide field, not on the execution context
    // backgroundExecutor.submit(this::processCommandQueue);
  }

  public ChannelFuture writeToChannel(RedisResponse response) {
    return channel.writeAndFlush(response.encode(byteBufAllocator), channel.newPromise())
        .addListener((ChannelFutureListener) f -> {
          response.afterWrite();
          logResponse(response, channel.remoteAddress(), f.cause());
        });
  }

  private void processCommandQueue() throws Exception {
    while (true) {
      Command command = takeCommandFromQueue();
      if (command == TERMINATE_COMMAND) {
        return;
      }
      try {
        executeCommand(command);
        redisStats.incCommandsProcessed();
      } catch (Throwable ex) {
        exceptionCaught(command.getChannelHandlerContext(), ex);
      }
    }
  }

  private Command takeCommandFromQueue() {
    try {
      return commandQueue.take();
    } catch (InterruptedException e) {
      logger.info("Command queue thread interrupted");
      return TERMINATE_COMMAND;
    }
  }

  /**
   * This will handle the execution of received commands
   */
  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    Command command = (Command) msg;
    command.setChannelHandlerContext(ctx);

    executeCommand(command);
    // if (!channelInactive.get()) {
    // commandQueue.put(command);
    // }
  }

  /**
   * Exception handler for the entire pipeline
   */
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    RedisResponse exceptionResponse = getExceptionResponse(ctx, cause);
    if (exceptionResponse != null) {
      writeToChannel(exceptionResponse);
    }
  }

  public EventLoopGroup getSubscriberGroup() {
    return subscriberGroup;
  }

  public synchronized void changeChannelEventLoopGroup(EventLoopGroup newGroup,
      Consumer<Boolean> callback) {
    if (newGroup.equals(channel.eventLoop())) {
      // already registered with newGroup
      callback.accept(true);
      return;
    }
    channel.deregister().addListener((ChannelFutureListener) future -> {
      boolean registerSuccess = true;
      synchronized (channel) {
        if (!channel.isRegistered()) {
          try {
            newGroup.register(channel).sync();
          } catch (Exception e) {
            logger.warn("Unable to register new EventLoopGroup: {}", e.getMessage());
            registerSuccess = false;
          }
        }
      }
      callback.accept(registerSuccess);
    });
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    if (channelInactive.compareAndSet(false, true)) {
      if (logger.isDebugEnabled()) {
        logger.debug("GeodeRedisServer-Connection closing with " + ctx.channel().remoteAddress());
      }

      commandQueue.clear();
      commandQueue.offer(TERMINATE_COMMAND);
      redisStats.removeClient();
      ctx.channel().close();
      ctx.close();
    }
  }

  private void executeCommand(Command command) {
    try {
      if (logger.isDebugEnabled()) {
        logger.debug("Executing Redis command: {} - {}", command,
            channel.remoteAddress().toString());
      }

      if (command.isUnknown()) {
        writeToChannel(command.execute(this));
        return;
      }

      if (!isAuthenticated()) {
        writeToChannel(handleUnAuthenticatedCommand(command));
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
        writeToChannel(command.execute(this));
      } finally {
        redisStats.endCommand(command.getCommandType(), start);
      }

      if (command.isOfType(RedisCommandType.QUIT)) {
        channelInactive(command.getChannelHandlerContext());
      }
    } catch (Exception e) {
      logger.warn("Execution of Redis command {} failed: {}", command, e);
      throw e;
    }
  }

  private boolean allowUnsupportedCommands() {
    return allowUnsupportedSupplier.get();
  }

  private RedisResponse handleUnAuthenticatedCommand(Command command) {
    RedisResponse response;
    if (command.isOfType(RedisCommandType.AUTH)) {
      response = command.execute(this);
    } else {
      response = RedisResponse.customError(RedisConstants.ERROR_NOT_AUTH);
    }
    return response;
  }

  private void logResponse(RedisResponse response, Object extraMessage, Throwable cause) {
    if (logger.isDebugEnabled() && response != null) {
      ByteBuf buf = response.encode(new UnpooledByteBufAllocator(false));
      if (cause == null) {
        logger.debug("Redis command returned: {} - {}",
            Command.getHexEncodedString(buf.array(), buf.readableBytes()), extraMessage);
      } else {
        logger.debug("Redis command FAILED to return: {} - {}",
            Command.getHexEncodedString(buf.array(), buf.readableBytes()), extraMessage, cause);
      }
    }
  }

  /**
   * {@link ByteBuf} allocator for this context. All executors must use this pooled allocator as
   * opposed to having unpooled buffers for maximum performance
   *
   * @return allocator instance
   */
  public ByteBufAllocator getByteBufAllocator() {
    return this.byteBufAllocator;
  }

}
