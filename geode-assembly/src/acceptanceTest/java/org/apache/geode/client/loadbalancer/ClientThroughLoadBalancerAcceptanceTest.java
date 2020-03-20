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
package org.apache.geode.client.loadbalancer;

import static com.palantir.docker.compose.execution.DockerComposeExecArgument.arguments;
import static com.palantir.docker.compose.execution.DockerComposeExecOption.options;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;

import com.palantir.docker.compose.DockerComposeRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;

/**
 * Test the case where a client is configured to connect to servers through a load balancer, where
 * all of the servers report the same hostname-for-clients to be the load balancer address.
 *
 * This test runs the servers in a docker container to ensure they are not resolvable or reachable
 * except through the load balancer.
 */
public class ClientThroughLoadBalancerAcceptanceTest {

  private static final URL DOCKER_COMPOSE_PATH =
      ClientThroughLoadBalancerAcceptanceTest.class
          .getResource("docker-compose.yml");

  @Rule
  public DockerComposeRule docker = DockerComposeRule.builder()
      .file(DOCKER_COMPOSE_PATH.getPath())
      .build();
  private ClientCache cache;

  @Before
  public void before() throws IOException, InterruptedException {
    docker.exec(options("-T"), "geode1",
        arguments("gfsh", "run", "--file=/geode/scripts/server1.gfsh"));
    docker.exec(options("-T"), "geode2",
        arguments("gfsh", "run", "--file=/geode/scripts/server2.gfsh"));
  }

  @Before
  public void createCache() {
    int locatorPort = docker.containers()
        .container("haproxy")
        .port(10334)
        .getExternalPort();
    cache = new ClientCacheFactory()
        .addPoolLocator("localhost", locatorPort)
        .create();
  }

  @After
  public void closeCache() {
    cache.close();

  }

  /**
   * Test that if a single server is stopped, the client fail over to the second server.
   *
   * This requires the client to realize there is more than one server and retry the operation
   * to the load balancer. The locator must also be aware that there are still available servers
   */
  @Test
  public void clientCanFailoverIfOneServerIsStopped()
      throws IOException, InterruptedException {
    Region<String, String> region =
        cache.<String, String>createClientRegionFactory(ClientRegionShortcut.PROXY)
            .create("jellyfish");
    region.destroy("hello");
    region.put("hello", "world");

    docker.exec(options("-T"), "geode2",
        arguments("gfsh", "stop", "server", "--dir", "server2"));

    assertThat(region.get("hello")).isEqualTo("world");
  }


  /**
   * Test that pings are getting through to the correct server, and the client health
   * monitor does not disconnect the client.
   */
  @Test
  public void clientDoesntGetDisconnectedByHealthMonitor()
      throws IOException, InterruptedException {
    Region<String, String> region =
        cache.<String, String>createClientRegionFactory(ClientRegionShortcut.PROXY)
            .create("jellyfish");
    region.destroy("hello");
    region.put("hello", "world");

    // Wait for the client health monitor to kick a client out
    Thread.sleep(80 * 1000);

    assertThat(region.get("hello")).isEqualTo("world");
  }
}
