/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geode.cache.query.internal;

import static org.junit.Assert.assertTrue;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.query.internal.index.IndexManager;
import org.apache.geode.test.dunit.Host;
import org.apache.geode.test.dunit.Invoke;
import org.apache.geode.test.dunit.SerializableRunnableIF;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.dunit.cache.internal.JUnit4CacheTestCase;
import org.apache.geode.test.junit.categories.DistributedTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(DistributedTest.class)
public class SafeQueryTimeDUnitTest extends JUnit4CacheTestCase {

  private VM vm0;
  private VM vm1;

  @Before
  public void resetSafeQueryTime() {
    Host host = Host.getHost(0);
    vm0 = host.getVM(0);
    vm1 = host.getVM(1);
    vm0.invoke(() -> createRegion());
    vm1.invoke(() -> createRegion());
    Invoke.invokeInEveryVM(() -> IndexManager.resetIndexBufferTime());
  }

  @Test
  public void putUpdatesSafeQueryTime() {
    long start = System.currentTimeMillis();
    vm0.invoke(() -> getCache().getRegion("region").put("key", "value"));
    checkSafeQueryTime(start);
  }

  @Test
  public void invalidateUpdatesSafeQueryTime() {
    vm0.invoke(() -> getCache().getRegion("region").put("key", "value"));
    Invoke.invokeInEveryVM(() -> IndexManager.resetIndexBufferTime());
    long start = System.currentTimeMillis();
    vm0.invoke(() -> getCache().getRegion("region").invalidate("key"));
    checkSafeQueryTime(start);
  }

  @Test
  public void destroyUpdatesSafeQueryTime() {
    vm0.invoke(() -> getCache().getRegion("region").put("key", "value"));
    Invoke.invokeInEveryVM(() -> IndexManager.resetIndexBufferTime());
    long start = System.currentTimeMillis();
    vm0.invoke(() -> getCache().getRegion("region").destroy("key"));
    checkSafeQueryTime(start);
  }

  @Test
  public void giiUpdatesSafeQueryTime() {
    vm0.invoke(() -> getCache().getRegion("region").put("key", "value"));
    vm1.invoke(() -> getCache().getRegion("region").localDestroyRegion());
    Invoke.invokeInEveryVM(() -> IndexManager.resetIndexBufferTime());
    long start = System.currentTimeMillis();
    vm1.invoke(() -> createRegion());
    checkSafeQueryTime(start);
  }

  private void checkSafeQueryTime(final long start) {
    final SerializableRunnableIF checkSafeQueryTime = () -> {
      assertTrue("Safe query time is too small. Failed check " + IndexManager.getIndexBufferTime() + " >= " + start, IndexManager.getIndexBufferTime() >= start);
    };

    vm0.invoke(checkSafeQueryTime);
    vm1.invoke(checkSafeQueryTime);
  }


  public void createRegion() {
    Region region = getCache().createRegionFactory(RegionShortcut.REPLICATE)
      .create("region");
  }

}
