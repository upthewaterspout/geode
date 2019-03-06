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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.distributed.internal.membership.MemberAttributes;
import org.apache.geode.internal.Version;
import org.apache.geode.internal.cache.tier.InterestType;
import org.apache.geode.internal.jndi.JNDIInvoker;
import org.apache.geode.internal.logging.LogService;
import org.apache.geode.internal.logging.log4j.FastLogger;
import org.apache.geode.internal.util.BlobHelper;
import org.apache.geode.test.concurrency.ConcurrentTestRunner;
import org.apache.geode.test.concurrency.ParallelExecutor;
import org.apache.geode.test.concurrency.annotation.ConcurrentTestConfig;
import org.apache.geode.test.concurrency.fates.FatesConfig;
import org.apache.geode.test.concurrency.fates.FatesRunner;

/**
 * Tests of concurrent operations on a single replicated region
 */
@RunWith(ConcurrentTestRunner.class)
@ConcurrentTestConfig(runner = FatesRunner.class)
@FatesConfig(atomicClasses = {FastLogger.class, LogService.class, Logger.class})
public class ConcurrentRegionAccessIntegrationTest {

  @Test
  public void test(ParallelExecutor executor) {
    InternalDistributedSystem.ALLOW_MULTIPLE_SYSTEMS = true;
    System.setProperty(DistributionConfig.GEMFIRE_PREFIX + "ignoreJTA", "true");
    Cache cache = new CacheFactory().set("locators", "").create();
    Region region = cache.createRegionFactory(RegionShortcut.REPLICATE).create("region");

//    executor.inParallel("put1", region.put(1,1));
//    ex
    region.put(1,1);

    assertEquals(1, region.get(1));
    cache.close();
  }


}
