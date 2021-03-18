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

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.shell.converters.AvailableCommandsConverter;

import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.partition.PartitionRegionHelper;
import org.apache.geode.internal.cache.BucketRegion;
import org.apache.geode.internal.cache.PartitionedRegion;
import org.apache.geode.internal.cache.RegionEntry;
import org.apache.geode.internal.size.ObjectGraphSizer;
import org.apache.geode.redis.GeodeRedisServerRule;
import org.apache.geode.redis.internal.RegionProvider;

public class MemoryOverheadIntegrationTest extends AbstractMemoryOverheadIntegrationTest {
  protected static ObjectGraphSizer.ObjectFilter filter =
      (parent, object) -> !(object instanceof AvailableCommandsConverter);

  @ClassRule
  public static GeodeRedisServerRule server = new GeodeRedisServerRule();

  @Before
  public void assignBuckets() {
    //TODO - this hack is just because we are using a fake cluster mode that requires buckets
    //to be created up front
    PartitionRegionHelper.assignBucketsToPartitions(CacheFactory.getAnyInstance().getRegion(
        RegionProvider.REDIS_DATA_REGION));
  }

  @Test
  public void showStringEntryHistogram() throws IllegalAccessException {

    //Set the empty key
    String response = jedis.set("", "");
    assertThat(response).isEqualTo("OK");

    //Extract the region entry from geode and show it's size

    final PartitionedRegion
        dataRegion =
        (PartitionedRegion) CacheFactory.getAnyInstance().getRegion(RegionProvider.REDIS_DATA_REGION);
    final Object redisKey = dataRegion.keys().iterator().next();
    BucketRegion bucket = dataRegion.getBucketRegion(redisKey);
    RegionEntry entry = bucket.entries.getEntry(redisKey);
    System.out.println(ObjectGraphSizer.histogram(entry, false));
  }

  @Override
  public int getPort() {
    return server.getPort();
  }

  @Override
  long getUsedMemory() {
    final Runtime runtime = Runtime.getRuntime();
    try {
      return ObjectGraphSizer.size(server.getServer(), filter, true);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Couldn't compute size of cache", e);
    }
  }
}
