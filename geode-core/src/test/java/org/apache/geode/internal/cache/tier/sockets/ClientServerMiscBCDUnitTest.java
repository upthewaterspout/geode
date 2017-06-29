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
package org.apache.geode.internal.cache.tier.sockets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolManager;
import org.apache.geode.cache.client.internal.PoolImpl;
import org.apache.geode.cache.client.internal.QueueStateImpl.SequenceIdAndExpirationObject;
import org.apache.geode.distributed.Locator;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.internal.AvailablePort;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.internal.cache.EventID;
import org.apache.geode.internal.cache.LocalRegion;
import org.apache.geode.internal.cache.ha.ThreadIdentifier;
import org.apache.geode.test.dunit.Host;
import org.apache.geode.test.dunit.NetworkUtils;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.dunit.standalone.VersionManager;
import org.apache.geode.test.junit.categories.BackwardCompatibilityTest;
import org.apache.geode.test.junit.categories.ClientServerTest;
import org.apache.geode.test.junit.categories.DistributedTest;
import org.apache.geode.test.junit.runners.CategoryWithParameterizedRunnerFactory;
import org.awaitility.Awaitility;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Category({DistributedTest.class, ClientServerTest.class, BackwardCompatibilityTest.class})
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(CategoryWithParameterizedRunnerFactory.class)
public class ClientServerMiscBCDUnitTest extends ClientServerMiscDUnitTest {
  @Parameterized.Parameters
  public static Collection<String> data() {
    List<String> result = VersionManager.getInstance().getVersionsWithoutCurrent();
    if (result.size() < 1) {
      throw new RuntimeException("No older versions of Geode were found to test against");
    } else {
      System.out.println("running against these versions: " + result);
    }
    return result;
  }

  public ClientServerMiscBCDUnitTest(String version) {
    super();
    testVersion = version;
  }

  @Test
  public void testSubscriptionWithCurrentServerAndOldClients() throws Exception {
    // start server first
    int serverPort = initServerCache(true);
    VM client1 = Host.getHost(0).getVM(testVersion, 1);
    VM client2 = Host.getHost(0).getVM(testVersion, 3);
    String hostname = NetworkUtils.getServerHostName(Host.getHost(0));
    client1.invoke("create client1 cache", () -> {
      createClientCache(hostname, serverPort);
      populateCache();
      registerInterest();
    });
    client2.invoke("create client2 cache", () -> {
      Pool ignore = createClientCache(hostname, serverPort);
    });

    client2.invoke("putting data in client2", () -> putForClient());

    // client1 will receive client2's updates asynchronously
    client1.invoke(() -> {
      Region r2 = getCache().getRegion(REGION_NAME2);
      MemberIDVerifier verifier = (MemberIDVerifier) ((LocalRegion) r2).getCacheListener();
      Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() -> verifier.eventReceived);
    });

    // client2's update should have included a memberID - GEODE-2954
    client1.invoke(() -> {
      Region r2 = getCache().getRegion(REGION_NAME2);
      MemberIDVerifier verifier = (MemberIDVerifier) ((LocalRegion) r2).getCacheListener();
      assertFalse(verifier.memberIDNotReceived);
    });
  }

  private static Object threadId;

  @Test
  public void clientShouldOnlyHaveOneThreadIDAfterServerUpgraded() throws Exception {
    // start server first

    VM locatorVM = Host.getHost(0).getVM(testVersion, 0);

    int locatorPort = locatorVM.invoke(() -> {
      Locator locator = Locator.startLocatorAndDS(0, new File("oldLocator"), new Properties());
      return locator.getPort();
    });

    VM oldServerVM = Host.getHost(0).getVM(testVersion, 1);
    VM newServerVM = Host.getHost(0).getVM(2);
    VM client1 = Host.getHost(0).getVM(testVersion, 3);
    VM feed = Host.getHost(0).getVM(testVersion, 4);

    int serverPort1 = oldServerVM.invoke(() -> createServerCache(true, 800, true, locatorPort));
    int serverPort2 = newServerVM.invoke(() -> createServerCache(true, 800, true, locatorPort));
    feed.invoke(() -> createServerCache(true, 800, true, locatorPort));
    stopServer(newServerVM);
    stopServer(feed);

    String hostname = NetworkUtils.getServerHostName(Host.getHost(0));

    // Create an old version client, which should connect to the old version server
    client1.invoke("create client1 cache", () -> {
      createClientCache(hostname, serverPort1, serverPort2);
      populateCache();
      registerInterest();
    });

    // Put an entry in the feed (an old version peer
    feed.invoke("putting data in client2", () -> {
      Cache cache = getCache();
      Region r2 = cache.getRegion(Region.SEPARATOR + REGION_NAME2);

      r2.put("key1", "some junk");
      threadId = EventID.getThreadLocalDataForHydra();
    });

    // Wait for client1 to get the event from the old version server
    client1.invoke(() -> {
      Cache cache = getCache();
      Region r2 = cache.getRegion(Region.SEPARATOR + REGION_NAME2);
      Awaitility.await().atMost(1, TimeUnit.MINUTES).until(() -> r2.get("key1") != null);
    });

    // Stop the old version server and start a current version server
    startServer(newServerVM);
    stopServer(oldServerVM);

    // Make sure the client has failed over to the new server
    Thread.sleep(10000);
    client1.invoke(() -> {
      Cache cache = getCache();
      Region r2 = cache.getRegion(Region.SEPARATOR + REGION_NAME2);
      PoolImpl pool = (PoolImpl) PoolManager.find(r2);
      // Wait for the client to recover it's queue
      pool.getPrimary();
    });

    // Do a put which will go through the new server
    feed.invoke("putting data in client2", () -> {
      EventID.setThreadLocalDataForHydra(threadId);
      Cache cache = getCache();
      Region r2 = cache.getRegion(Region.SEPARATOR + REGION_NAME2);

      r2.put("key2", "some junk");
    });

    client1.invoke(() -> {
      Region r2 = getCache().getRegion(REGION_NAME2);
      // Make sure the client receives the new event from the server
      Awaitility.await().atMost(1, TimeUnit.MINUTES).until(() -> r2.get("key2") != null);


      // Check that we only have 1 thread id from the feed
      PoolImpl pool = (PoolImpl) PoolManager.find(r2);
      final Map<ThreadIdentifier, SequenceIdAndExpirationObject> map =
          pool.getThreadIdToSequenceIdMap();
      String memberIds = map.entrySet().stream()
          .map(
              entry -> entry.getKey().expensiveToString() + "->" + entry.getValue().getSequenceId())
          .collect(Collectors.joining(", "));
      // We expect 1 entry for the Marker message from each server, plus 1 entry from our feed
      assertEquals("Should be three entries in thread id in map: " + memberIds, 3, map.size());
    });

  }

  protected void startServer(final VM vm) {
    vm.invoke(() -> getCache().getCacheServers().iterator().next().start());
  }

  protected void stopServer(final VM vm) {
    vm.invoke(() -> getCache().getCacheServers().iterator().next().stop());
  }

  @Test
  public void testDistributedMemberBytesWithCurrentServerAndOldClient() throws Exception {
    // Start current version server
    int serverPort = initServerCache(true);

    // Start old version client and do puts
    VM client = Host.getHost(0).getVM(testVersion, 1);
    String hostname = NetworkUtils.getServerHostName(Host.getHost(0));
    client.invoke("create client cache", () -> {
      createClientCache(hostname, serverPort);
      populateCache();
    });

    // Get client member id byte array on client
    byte[] clientMembershipIdBytesOnClient =
        client.invoke(() -> getClientMembershipIdBytesOnClient());

    // Get client member id byte array on server
    byte[] clientMembershipIdBytesOnServer =
        server1.invoke(() -> getClientMembershipIdBytesOnServer());

    // Verify member id bytes on client and server are equal
    String complaint = "size on client=" + clientMembershipIdBytesOnClient.length
        + "; size on server=" + clientMembershipIdBytesOnServer.length;
    assertTrue(complaint,
        Arrays.equals(clientMembershipIdBytesOnClient, clientMembershipIdBytesOnServer));
  }

  private byte[] getClientMembershipIdBytesOnClient() {
    return EventID.getMembershipId(getCache().getDistributedSystem());
  }

  private byte[] getClientMembershipIdBytesOnServer() {
    Set cpmIds = ClientHealthMonitor.getInstance().getClientHeartbeats().keySet();
    assertEquals(1, cpmIds.size());
    ClientProxyMembershipID cpmId = (ClientProxyMembershipID) cpmIds.iterator().next();
    return EventID.getMembershipId(cpmId);
  }
}
