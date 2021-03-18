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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.setAllowComparingPrivateFields;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntToLongFunction;
import java.util.stream.IntStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import org.apache.geode.test.dunit.rules.RedisPortSupplier;

public abstract class AbstractMemoryOverheadIntegrationTest implements RedisPortSupplier {

  private static final int WARM_UP_ENTRY_COUNT = 1000;
  private static final int TOTAL_ENTRY_COUNT = 10000;
  private static final int SAMPLE_INTERVAL = 100;
  public static final String
      LARGE_STRING =
      "value_that_will_force_redis_to_not_use_a_ziplist______________________________________________________________";
  protected Jedis jedis;

  @Before
  public void setUp() {
    jedis = new Jedis("localhost", getPort(), 10000000);
  }

  @After
  public void tearDown() {
    jedis.flushAll();
    jedis.close();
  }

  abstract long getUsedMemory();

  @Test
  @Ignore
  public void measureSizeOfSmallStrings() {

    doMeasurements(key -> {
      String keyString = String.format("key-%10d",key);
      String valueString = String.format("value-%10d",key);
      String response = jedis.set(keyString, valueString);
      assertThat(response).isEqualTo("OK");

      //Note - jedis convert strings to bytes with the UTF-8 charset
      //Since the strings above are all ASCII, the length == the number of bytes
      return keyString.length() + valueString.length();
    });
  }

  @Test
  public void measureSizeOfSmallHashes() {
      doMeasurements(key -> {
        String keyString = String.format("key-%10d", key);
        String mapKey = "key";
        Long response = jedis.hset(keyString, mapKey, LARGE_STRING);
//        assertThat(response).isEqualTo(1);
        return keyString.length() + mapKey.length() + LARGE_STRING.length();
      });
  }

  @Test
  public void measureSizeOfHashEntries() {
      doMeasurements(key -> {

        String keyString = String.format("key-%10d",key);
        String valueString = String.format("%s value-%10d",LARGE_STRING, key);
        Long response = jedis.hset("TestSet", keyString, valueString);
//        assertThat(response).isEqualTo(1);

        return keyString.length() + valueString.length();
      });
  }

  @Test
  public void measureSizeOfSmallSets() {
    doMeasurements(key -> {
      String keyString = String.format("key-%10d", key);
      Long
          response =
          jedis.sadd(keyString, LARGE_STRING);
//        assertThat(response).isEqualTo(1);
      return keyString.length() + LARGE_STRING.length();
    });
  }

  @Test
  public void measureSizeOfSetEntries() {
    doMeasurements(key -> {

      String valueString = String.format("%s value-%10d",LARGE_STRING, key);
      Long response = jedis.sadd("TestSet", valueString);
//        assertThat(response).isEqualTo(1);

      return valueString.length();
    });
  }

  //TODO sets

  //TODO sets that redis optimizes (small sets, integers?)

  //TODO memory fragmentation test - try to break redis's allocator

  //TODO

  private void doMeasurements(IntToLongFunction addEntry) {
    //Warmup
    for(int i = 0; i < WARM_UP_ENTRY_COUNT; i++) {
      addEntry.applyAsLong(-i);
    }

    //Perform measurements
    long baseline = getUsedMemory();
    long totalDataSize = 0;
    System.out.printf("%20s, %20s, %20s", "Used Memory", "Total Mem Per Entry", "Overhead Per Entry\n");
    for(int i = 0; i < TOTAL_ENTRY_COUNT; i++) {
      totalDataSize += addEntry.applyAsLong(i);
      if(i % SAMPLE_INTERVAL == (SAMPLE_INTERVAL-1)) {
        long currentMemory = getUsedMemory() - baseline;
        long perEntryMemory = currentMemory / i;
        long perEntryOverhead = (currentMemory - totalDataSize) / i;
        System.out.printf("%20d, %20d, %20d\n", currentMemory, perEntryMemory, perEntryOverhead);
      }
    }
  }
}
