package org.apache.geode.redis.internal;

import java.io.IOException;
import java.net.Socket;
import java.util.function.Supplier;

import org.apache.geode.cache.server.CacheServer;
import org.apache.geode.internal.cache.CacheServerImpl;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.cache.tier.Acceptor;
import org.apache.geode.internal.cache.tier.CachedRegionHelper;
import org.apache.geode.internal.cache.tier.CommunicationMode;
import org.apache.geode.internal.cache.tier.sockets.CacheServerStats;
import org.apache.geode.internal.cache.tier.sockets.ServerConnection;
import org.apache.geode.internal.cache.tier.sockets.ServerConnectionFactory;
import org.apache.geode.internal.security.SecurityService;
import org.apache.geode.redis.internal.pubsub.PubSub;
import org.apache.geode.redis.internal.statistics.RedisStats;

public class CacheServerBasedRedisServer {
  private final InternalCache cache;
  private final String bindAddress;
  private final int port;
  private final RegionProvider regionProvider;
  private final PubSub pubSub;
  private final Supplier<Boolean> allowUnsupportedSupplier;
  private final Runnable shutdownInvoker;
  private final RedisStats redisStats;
  private CacheServer cacheServer;

  public CacheServerBasedRedisServer(InternalCache cache, String bindAddress, int port,
                                     RegionProvider regionProvider, PubSub pubSub,
                                     Supplier<Boolean> allowUnsupportedSupplier,
                                     Runnable shutdownInvoker,
                                     RedisStats redisStats) {

    this.cache = cache;
    this.bindAddress = bindAddress;
    this.port = port;
    this.regionProvider = regionProvider;
    this.pubSub = pubSub;
    this.allowUnsupportedSupplier = allowUnsupportedSupplier;
    this.shutdownInvoker = shutdownInvoker;
    this.redisStats = redisStats;
    cacheServer = cache.addCacheServer();
    cacheServer.setBindAddress(bindAddress);
    cacheServer.setPort(port);
    ((CacheServerImpl) cacheServer).setServerConnectionFactory(new RedisServerConnectionFactory());
  }

  public void start() throws IOException {
    cacheServer.start();
  }

  public int getPort() {
    return cacheServer.getPort();
  }

  public void stop() {
    this.cacheServer.stop();
  }

  private class RedisServerConnectionFactory extends ServerConnectionFactory {
    @Override
    protected ServerConnection makeServerConnection(Socket socket, InternalCache cache,
                                                    CachedRegionHelper cachedRegionHelper,
                                                    CacheServerStats stats,
                                                    int hsTimeout, int socketBufferSize,
                                                    String communicationModeStr,
                                                    byte communicationMode,
                                                    Acceptor acceptor,
                                                    SecurityService securityService)
        throws IOException {
      return new RedisServerConnection(socket, cache, cachedRegionHelper, stats, hsTimeout, socketBufferSize,
          communicationModeStr, communicationMode, acceptor, null, securityService, redisStats, regionProvider, pubSub, allowUnsupportedSupplier, shutdownInvoker);
    }

    @Override
    public CommunicationMode getKnownMode() {
      return CommunicationMode.RedisProtocol;
    }
  }

}
