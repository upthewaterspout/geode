package org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;

import org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty.Handshake;

public class HandshakeProcessor extends SimpleChannelInboundHandler<Handshake> {
  static final AttributeKey<Handshake> HANDSHAKE_ATTRIBUTE =
      AttributeKey.newInstance("GEODE_HANDSHAKE");

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Handshake msg)
      throws Exception {
    ctx.channel().attr(HANDSHAKE_ATTRIBUTE).set(msg);
  }
}
