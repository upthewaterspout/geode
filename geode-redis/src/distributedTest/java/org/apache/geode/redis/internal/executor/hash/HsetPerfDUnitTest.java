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

package org.apache.geode.redis.internal.executor.hash;

import static org.apache.geode.distributed.ConfigurationProperties.MAX_WAIT_TIME_RECONNECT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPoolConfig;

import org.apache.geode.redis.ConcurrentLoopingThreads;
import org.apache.geode.test.awaitility.GeodeAwaitility;
import org.apache.geode.test.dunit.rules.MemberVM;
import org.apache.geode.test.dunit.rules.RedisClusterStartupRule;

public class HsetPerfDUnitTest {

  @ClassRule
  public static RedisClusterStartupRule clusterStartUp = new RedisClusterStartupRule(4);

  private static final String LOCAL_HOST = "127.0.0.1";
  private static final int HASH_SIZE = 30;
  private static final int JEDIS_TIMEOUT =
      Math.toIntExact(GeodeAwaitility.getTimeout().toMillis());
  private static Jedis jedis1;
  private static Jedis jedis2;
  private static Jedis jedis3;

  private static Properties locatorProperties;

  private static MemberVM locator;
  private static MemberVM server1;
  private static MemberVM server2;
  private static MemberVM server3;

  private static int redisServerPort1;
  private static int redisServerPort2;
  private static int redisServerPort3;
  private static JedisCluster jedisCluster;

  @BeforeClass
  public static void classSetup() {
    locatorProperties = new Properties();
    locatorProperties.setProperty(MAX_WAIT_TIME_RECONNECT, "15000");

    locator = clusterStartUp.startLocatorVM(0, locatorProperties);
    server1 = clusterStartUp.startRedisVM(-1, locator.getPort());
    server2 = clusterStartUp.startRedisVM(2, locator.getPort());

    redisServerPort1 = clusterStartUp.getRedisPort(-1);
    redisServerPort2 = clusterStartUp.getRedisPort(2);

    final JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(-1);
    poolConfig.setMaxIdle(-1);
    poolConfig.setLifo(false);
    jedisCluster = new JedisCluster(new HostAndPort(LOCAL_HOST, redisServerPort1), 3600, poolConfig);
//    jedis1 = new Jedis(LOCAL_HOST, redisServerPort1, JEDIS_TIMEOUT);
  }

  @Before
  public void testSetup() {
//    jedis1.flushAll();
  }

  @AfterClass
  public static void tearDown() {
//    jedis1.disconnect();
    jedisCluster.close();

    server1.stop();
    server2.stop();
  }

  @Test
  public void testGetPut() {

    String key = "key";

    Map<String, String> testMap = makeHashMap(HASH_SIZE, "field-", "value-");

    long keyRange = 10;

    for (int i = 0; i < keyRange; i++) {
      jedisCluster.hset(key + i, "key1", "value1");
      jedisCluster.hset(key + i, "key2", "value2");
      jedisCluster.hset(key + i, "key3", "value3");
    }

    for (int i = 0; i < keyRange; i++) {
      assertThat(jedisCluster.hget(key + i, "key1")).isEqualTo("value1");
      assertThat(jedisCluster.hget(key + i, "key2")).isEqualTo("value2");
      assertThat(jedisCluster.hget(key + i, "key3")).isEqualTo("value3");
    }

  }

  @Test
  public void performanceTest() {

    String key = "key";

    Map<String, String> testMap = makeHashMap(HASH_SIZE, "field-", "value-");

    long keyRange = 1000;

    while(true) {
      for (int i = 0; i < keyRange; i++) {
        jedisCluster.hset(key + i, testMap);
      }
    }
  }

  private Consumer<Integer> makeHSetConsumer(Map<String, String> testMap, String[] fields,
      String hashKey, Jedis jedis) {
    Consumer<Integer> consumer = (i) -> {
      String field = fields[i];
      jedis.hset(hashKey, field, testMap.get(field));
    };

    return consumer;
  }

  private Map<String, String> makeHashMap(int hashSize, String baseFieldName,
      String baseValueName) {
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < hashSize; i++) {
      map.put(baseFieldName + i, baseValueName + i);
    }
    return map;
  }
}
