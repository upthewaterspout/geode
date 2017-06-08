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
package org.apache.geode.session.tests;

import static org.junit.Assert.assertEquals;

import org.apache.geode.test.dunit.cache.internal.JUnit4CacheTestCase;
import org.apache.geode.test.junit.categories.DistributedTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.net.URISyntaxException;

@Category(DistributedTest.class)
public abstract class CargoTestBase extends JUnit4CacheTestCase {

  private transient Client client;
  private transient ContainerManager manager;

  public abstract ContainerInstall getInstall();

  @Before
  public void setup() {
    client = new Client();
    manager = new ContainerManager();
  }

  @After
  public void stop() {
    manager.stopAllActiveContainers();
  }

  private void containersShouldReplicateSession() throws IOException, URISyntaxException {
    if (manager.numContainers() < 2)
      throw new IllegalArgumentException(
          "Bad ContainerManager, must have 2 or more containers for this test");

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    Client.Response resp = client.get(null);
    String cookie = resp.getSessionCookie();

    for (int i = 1; i < manager.numContainers(); i++) {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(null);

      assertEquals(cookie, resp.getSessionCookie());
    }
  }

  @Test
  public void twoTomcatContainersShouldReplicateCookies() throws IOException, URISyntaxException {
    manager.addContainers(3, getInstall());

    manager.startAllInactiveContainers();
    containersShouldReplicateSession();
  }

  private void containersShouldHavePersistentSessionData() throws IOException, URISyntaxException {
    String key = "value_testSessionPersists";
    String value = "Foo";

    if (manager.numContainers() < 2)
      throw new IllegalArgumentException(
          "Bad ContainerManager, must have 2 or more containers for this test");

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    Client.Response resp = client.set(key, value);
    String cookie = resp.getSessionCookie();

    for (int i = 0; i < manager.numContainers(); i++) {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals("Sessions are not replicating properly", cookie, resp.getSessionCookie());
      assertEquals(value, resp.getResponse());
    }
  }

  @Test
  public void threeTomcatContainersShouldHavePersistentSessionData()
      throws IOException, URISyntaxException {
    manager.addContainers(3, getInstall());

    manager.startAllInactiveContainers();
    containersShouldHavePersistentSessionData();
  }

  private void failureShouldStillAllowOtherContainersDataAccess()
      throws IOException, URISyntaxException {
    String key = "value_testSessionPersists";
    String value = "Foo";

    if (manager.numContainers() < 2)
      throw new IllegalArgumentException(
          "Bad ContainerManager, must have 2 or more containers for this test");

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    Client.Response resp = client.set(key, value);
    String cookie = resp.getSessionCookie();

    manager.stopContainer(0);

    for (int i = 1; i < manager.numContainers(); i++) {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals("Sessions are not replicating properly", cookie, resp.getSessionCookie());
      assertEquals(value, resp.getResponse());
    }
  }

  @Test
  public void containerFailureShouldStillAllowTwoOtherContainersToAccessSessionData()
      throws IOException, URISyntaxException {
    manager.addContainers(3, getInstall());

    manager.startAllInactiveContainers();
    failureShouldStillAllowOtherContainersDataAccess();
  }

  private void invalidationShouldRemoveValueAccessForAllContainers()
      throws IOException, URISyntaxException {
    String key = "value_testInvalidate";
    String value = "Foo";

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    Client.Response resp = client.set(key, value);
    String cookie = resp.getSessionCookie();

    client.invalidate();

    for (int i = 0; i < manager.numContainers(); i++) {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals("", resp.getResponse());
    }
  }

  @Test
  public void invalidateShouldNotAllowContainerToAccessKeyValue()
      throws IOException, URISyntaxException {
    manager.addContainers(2, getInstall());

    manager.startAllInactiveContainers();
    invalidationShouldRemoveValueAccessForAllContainers();
  }

  private void containersShouldExpireInSetTimeframe()
      throws IOException, URISyntaxException, InterruptedException {
    String key = "value_testSessionExpiration";
    String value = "Foo";

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    Client.Response resp = client.set(key, value);
    String cookie = resp.getSessionCookie();

    client.setMaxInactive(1);

    for (int i = 0; i < manager.numContainers(); i++) {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals("Sessions are not replicating properly", cookie, resp.getSessionCookie());
      assertEquals(value, resp.getResponse());
    }

    Thread.sleep(5000);

    for (int i = 0; i < manager.numContainers(); i++) {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals("", resp.getResponse());
    }
  }

  @Test
  public void sessionShouldExpireInSetTimePeriod()
      throws IOException, URISyntaxException, InterruptedException {
    manager.addContainers(2, getInstall());

    manager.startAllInactiveContainers();
    containersShouldExpireInSetTimeframe();
  }

  private void containersShouldShareSessionExpirationReset()
      throws URISyntaxException, IOException, InterruptedException {
    int timeToExp = 5;
    String key = "value_testSessionExpiration";
    String value = "Foo";

    if (manager.numContainers() < 2)
      throw new IllegalArgumentException(
          "Bad ContainerManager, must have 2 or more containers for this test");

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    Client.Response resp = client.set(key, value);
    String cookie = resp.getSessionCookie();

    client.setMaxInactive(timeToExp);

    long startTime = System.currentTimeMillis();
    long curTime = System.currentTimeMillis();
    // Run for 2 times the set expiration time
    while (curTime - startTime < timeToExp * 2000) {
      client.get(key);
      curTime = System.currentTimeMillis();
    }

    for (int i = 0; i < manager.numContainers(); i++) {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals(cookie, resp.getSessionCookie());
      assertEquals(value, resp.getResponse());
    }
  }

  @Test
  public void sessionExpirationShouldResetAcrossAllContainers()
      throws IOException, URISyntaxException, InterruptedException {
    manager.addContainers(3, getInstall());

    manager.startAllInactiveContainers();
    containersShouldShareSessionExpirationReset();
  }

  private void containersShouldShareDataRemovals() throws IOException, URISyntaxException {
    String key = "value_testSessionRemove";
    String value = "Foo";

    if (manager.numContainers() < 2)
      throw new IllegalArgumentException(
          "Bad ContainerManager, must have 2 or more containers for this test");

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    Client.Response resp = client.set(key, value);
    String cookie = resp.getSessionCookie();

    for (int i = 0; i < manager.numContainers(); i++) {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals(cookie, resp.getSessionCookie());
      assertEquals(value, resp.getResponse());
    }

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    client.remove(key);

    for (int i = 0; i < manager.numContainers(); i++) {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals(cookie, resp.getSessionCookie());
      assertEquals(
          "Was expecting an empty response after removal. Double check to make sure that the enableLocalCache cacheProperty is set to false. This test is unreliable on servers which use a local cache.",
          "", resp.getResponse());
    }
  }

  @Test
  public void removingDataShouldRemoveDataAcrossAllContainers()
      throws IOException, URISyntaxException {
    manager.addContainers(2, getInstall());

    manager.startAllInactiveContainers();
    containersShouldShareDataRemovals();
  }
}
