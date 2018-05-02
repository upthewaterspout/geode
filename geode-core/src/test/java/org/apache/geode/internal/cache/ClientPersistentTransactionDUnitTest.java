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
 */
package org.apache.geode.internal.cache;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.server.CacheServer;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.dunit.cache.internal.JUnit4CacheTestCase;

public class ClientPersistentTransactionDUnitTest extends JUnit4CacheTestCase {

  private VM server;
  private VM client;

  @Before
  public void allowTransactions() {
    server = VM.getVM(0);
    client = VM.getVM(1);
    server.invoke(() -> TXManagerImpl.ALLOW_PERSISTENT_TRANSACTIONS = true);
  }

  @After
  public void disallowTransactions() {
    server.invoke(() -> TXManagerImpl.ALLOW_PERSISTENT_TRANSACTIONS = false);

  }

  @Test
  public void test() {

    createServer(server);

    putData(server);

    server.invoke(() -> getCache().close());

    int port = createServer(server);

    client.invoke(() -> {
      ClientCacheFactory factory = new ClientCacheFactory().addPoolServer("localhost", port);
      ClientCache cache = getClientCache(factory);
      cache.getCacheTransactionManager().begin();
      try {
        assertEquals("value 5",
            cache.createClientRegionFactory(ClientRegionShortcut.PROXY).create("region").get(5));
      } finally {
        cache.getCacheTransactionManager().rollback();
      }
    });

  }

  private void putData(final VM server) {
    server.invoke(() -> {
      IntStream.range(0, 20)
          .forEach(index -> getCache().getRegion("region").put(index, "value " + index));
    });
  }

  private int createServer(final VM server) {
    return server.invoke(() -> {
      CacheFactory cacheFactory = new CacheFactory();
      Cache cache = getCache(cacheFactory);
      cache.createRegionFactory(RegionShortcut.REPLICATE_PERSISTENT_OVERFLOW).create("region");
      CacheServer cacheServer = cache.addCacheServer();
      cacheServer.start();
      return cacheServer.getPort();
    });
  }
}
