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
package com.gemstone.gemfire.internal.cache.execute;

import com.gemstone.gemfire.cache.client.ClientCacheFactory;
import com.gemstone.gemfire.test.junit.categories.DistributedTest;

import org.junit.Ignore;
import org.junit.experimental.categories.Category;

/**
 * Tests onServers using multiple servers from a single client.
 */
@Category(DistributedTest.class)
public class FunctionServiceClientAccessorPRMultipleMembersMultihopDUnitTest extends FunctionServiceClientAccessorPRBase {

  @Override public void configureClient(final ClientCacheFactory cacheFactory) {
    cacheFactory.setPoolPRSingleHopEnabled(false);
    super.configureClient(cacheFactory);
  }

  @Override public int numberOfExecutions() {
    return 2;
  }

  @Ignore("Multihop clients don't support returning partial results after a cache close")
  @Override
  public void nonHAFunctionResultCollectorIsPassedPartialResultsAfterCloseCache() {
  }
}
