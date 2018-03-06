package org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty.client;

import java.net.InetSocketAddress;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldPrepender;

import org.apache.geode.distributed.internal.membership.api.MemberIdentifier;
import org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty.DataSerializableEncoder;
import org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty.Handshake;
import org.apache.geode.internal.serialization.DSFIDSerializer;

public class NettyClient {

  private final InetSocketAddress destination;
  private final DSFIDSerializer serializer;
  private final ChannelFuture channelFuture;
  private final EventLoopGroup workerGroup;

  private NettyClient(InetSocketAddress destination, DSFIDSerializer serializer,
      MemberIdentifier senderId) {
    this.destination = destination;
    this.serializer = serializer;
    workerGroup = new NioEventLoopGroup(1);
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(workerGroup);
    bootstrap.channel(NioSocketChannel.class);
    bootstrap.handler(new ChannelInitializer<SocketChannel>() {
      @Override
      protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(new LengthFieldPrepender(4));
        ch.pipeline().addLast(new DataSerializableEncoder(serializer));
      }
    });
    channelFuture = bootstrap.connect(destination);
    // TODO - blocking call. Ideally, we would handle failures to create the connection
    // asynchronously
    // and retry or some other action.
    channelFuture.syncUninterruptibly();
    Handshake<MemberIdentifier> handshake = new Handshake<>();
    handshake.setSender(senderId);
    channelFuture.channel().writeAndFlush(handshake);
  }

  public void send(Object message) {
    channelFuture.channel().writeAndFlush(message);
  }

  public static NettyClient connect(InetSocketAddress destination, DSFIDSerializer serializer,
      MemberIdentifier senderId) {
    NettyClient client = new NettyClient(destination, serializer, senderId);
    return client;
  }

  public ChannelFuture close() {
    final ChannelFuture close = channelFuture.channel().close();
    workerGroup.shutdownGracefully();
    return close;
  }
}
