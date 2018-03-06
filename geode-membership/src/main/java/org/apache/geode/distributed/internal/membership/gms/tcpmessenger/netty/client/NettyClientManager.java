package org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty.client;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.channel.ChannelFuture;

import org.apache.geode.distributed.internal.membership.api.MemberIdentifier;
import org.apache.geode.distributed.internal.membership.api.MembershipClosedException;
import org.apache.geode.internal.serialization.DSFIDSerializer;

public class NettyClientManager {
  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  private final Map<InetSocketAddress, NettyClient> clients = new ConcurrentHashMap<>();
  private DSFIDSerializer serializer;

  public NettyClientManager(DSFIDSerializer serializer) {
    this.serializer = serializer;
  }

  public void send(InetSocketAddress destination, Object message, MemberIdentifier senderId) {
    if(shutdown.get()) {
      throw new MembershipClosedException();
    }
    NettyClient client = clients.computeIfAbsent(destination,
        inetSocketAddress -> createClient(inetSocketAddress, senderId));
    client.send(message);
  }

  private NettyClient createClient(InetSocketAddress inetSocketAddress,
      MemberIdentifier senderId) {
    return NettyClient.connect(inetSocketAddress, serializer, senderId);
  }

  public void shutdown(InetSocketAddress clientAddress) {
    final ChannelFuture close = close(clientAddress);
    //TODO - needs a timeout?
    close.awaitUninterruptibly();
  }

  private ChannelFuture close(InetSocketAddress clientAddress) {
    NettyClient client = clients.get(clientAddress);
    final ChannelFuture close = client.close();
    return close;
  }

  public void close() {
    shutdown.set(true);
    //TODO - needs a timeout?
    clients.keySet().stream().map(this::close).forEach(ChannelFuture::awaitUninterruptibly);

  }
}
