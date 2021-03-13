package org.apache.geode.redis.internal.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import org.apache.geode.redis.internal.executor.RedisResponse;

public class RedisResponseToBytesEncoder extends MessageToByteEncoder<RedisResponse> {
  @Override
  protected void encode(ChannelHandlerContext ctx, RedisResponse msg, ByteBuf out)
      throws Exception {
    msg.encode(out);

  }
}
