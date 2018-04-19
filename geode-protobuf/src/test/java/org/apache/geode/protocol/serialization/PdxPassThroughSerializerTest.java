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
package org.apache.geode.protocol.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;

import com.google.protobuf.ByteString;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.distributed.ConfigurationProperties;
import org.apache.geode.pdx.PdxInstance;
import org.apache.geode.test.junit.categories.IntegrationTest;

@RunWith(JUnitQuickcheck.class)
@Category(IntegrationTest.class)
public class PdxPassThroughSerializerTest {

  private PdxPassThroughSerializer serializer;
  private static Cache cache;

  @BeforeClass
  public static void createCache() {
    cache = new CacheFactory().set(ConfigurationProperties.LOG_LEVEL, "error")
        .setPdxReadSerialized(true).create();
  }

  @Before
  public void createSerializer() {
    serializer = new PdxPassThroughSerializer();
    serializer.init(cache);
  }

  @AfterClass
  public static void tearDown() {
    cache.close();
  }


  @Property(trials = 10)
  public void testSymmetry(
      @PdxInstanceGenerator.ClassName("someclass") @PdxInstanceGenerator.FieldTypes({String.class,
          int.class, long.class, byte.class, byte[].class, double.class, PdxInstance.class,
          ArrayList.class}) @From(PdxInstanceGenerator.class) PdxInstance original)
      throws IOException, ClassNotFoundException {
    ByteString bytes = serializer.serialize(original);
    PdxInstance actual = (PdxInstance) serializer.deserialize(bytes);
    assertThat(original).isEqualTo(actual);
    assertEquals(actual, original);
  }
}
