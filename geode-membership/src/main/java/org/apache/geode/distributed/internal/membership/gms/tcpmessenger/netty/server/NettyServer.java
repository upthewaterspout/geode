package org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty.server;

import java.net.InetSocketAddress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.concurrent.Future;

import org.apache.geode.distributed.internal.membership.api.Message;
import org.apache.geode.distributed.internal.membership.gms.interfaces.MessageHandler;
import org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty.DataSerializableDecoder;
import org.apache.geode.internal.serialization.DSFIDSerializer;

public class NettyServer {

  private ChannelFuture channelFuture;
  private EventLoopGroup acceptorGroup;
  private EventLoopGroup workerGroup;
  private final MessageBroadcaster broadcaster;
  private final DSFIDSerializer serializer;

  public NettyServer(DSFIDSerializer serializer, MessageHandler defaultHandler) {
    this.serializer = serializer;
    this.broadcaster = new MessageBroadcaster(defaultHandler);
  }

  public void start(InetSocketAddress bindAddress) throws InterruptedException {
    acceptorGroup = new NioEventLoopGroup(1);
    workerGroup = new NioEventLoopGroup(1);

    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.localAddress(bindAddress)
        .group(acceptorGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) throws Exception {
            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
            ch.pipeline().addLast(new DataSerializableDecoder(serializer));
            ch.pipeline().addLast(new HandshakeProcessor());
            ch.pipeline().addLast(broadcaster);

          }
        });

    channelFuture = bootstrap.bind().sync();
  }

  public InetSocketAddress getAddress() {
    return (InetSocketAddress) channelFuture.channel().localAddress();
  }

  public void addHandler(Class<? extends Message> clazz, MessageHandler handler) {
    broadcaster.addHandler(clazz, handler);
  }

  public void close() {
    if(channelFuture != null) {
      final ChannelFuture closeFuture = channelFuture.channel().close();
      acceptorGroup.shutdownGracefully();
      workerGroup.shutdownGracefully();
      //TOOD - include timeouts?
      closeFuture.awaitUninterruptibly();
    }
  }

}
