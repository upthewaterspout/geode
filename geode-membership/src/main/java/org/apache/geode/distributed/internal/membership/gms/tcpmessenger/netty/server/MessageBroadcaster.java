package org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty.server;

import java.util.HashMap;
import java.util.Map;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.apache.geode.distributed.internal.membership.api.Message;
import org.apache.geode.distributed.internal.membership.gms.interfaces.MessageHandler;
import org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty.Handshake;

@ChannelHandler.Sharable
class MessageBroadcaster extends SimpleChannelInboundHandler<Message> {
  private final Map<Class<? extends Message>, MessageHandler> handlers = new HashMap<>();
  private final MessageHandler defaultHandler;

  public MessageBroadcaster(MessageHandler defaultHandler) {
    this.defaultHandler = defaultHandler;
  }


  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Message msg)
      throws Exception {
    Handshake handshake = ctx.channel().attr(HandshakeProcessor.HANDSHAKE_ATTRIBUTE).get();
    msg.setSender(handshake.getSender());

    MessageHandler handler = handlers.get(msg.getClass());
    if (handler == null) {
      handler = defaultHandler;
    }

    handler.processMessage(msg);
  }

  public void addHandler(Class clazz, MessageHandler handler) {
    this.handlers.put(clazz, handler);
  }
}
