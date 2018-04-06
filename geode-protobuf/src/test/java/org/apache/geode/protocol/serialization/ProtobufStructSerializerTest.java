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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.pdx.PdxInstance;
import org.apache.geode.test.junit.categories.IntegrationTest;
import org.apache.geode.test.junit.categories.UnitTest;

@RunWith(JUnitQuickcheck.class)
@Category(IntegrationTest.class)
public class ProtobufStructSerializerTest {

  private ProtobufStructSerializer serializer;
  private Cache cache;

  @Before
  public void createSerializer() {
    cache = mock(Cache.class);
    when(cache.createPdxInstanceFactory(any()))
        .then(invocation -> PdxInstanceFactoryMock.createMockFactory(invocation.getArgument(0)));
    serializer = new ProtobufStructSerializer();
    serializer.init(cache);
  }

  @After
  public void tearDown() {
    cache.close();
  }

  @Test
  public void testDeserialize() throws IOException, ClassNotFoundException {
    Struct struct = Struct.newBuilder()
        .putFields("field1", Value.newBuilder().setStringValue("value").build()).build();
    ByteString bytes = struct.toByteString();
    PdxInstance value = (PdxInstance) serializer.deserialize(bytes);

    assertEquals("value", value.getField("field1"));
  }

  @Test
  public void testSerialize() throws IOException, ClassNotFoundException {
    PdxInstance value = cache.createPdxInstanceFactory(ProtobufStructSerializer.PROTOBUF_STRUCT)
        .writeString("field1", "value").create();
    ByteString bytes = serializer.serialize(value);
    Struct struct = Struct.parseFrom(bytes);

    assertEquals("value", struct.getFieldsMap().get("field1").getStringValue());
  }

  @Property(trials = 10)
  public void testSymmetry(
      @PdxInstanceGenerator.ClassName(ProtobufStructSerializer.PROTOBUF_STRUCT)
      @PdxInstanceGenerator.FieldTypes({String.class, int.class})
      @From(PdxInstanceGenerator.class) PdxInstance original)
      throws IOException, ClassNotFoundException {
    ByteString bytes = serializer.serialize(original);
    Struct struct = Struct.parseFrom(bytes);
    bytes = struct.toByteString();
    PdxInstance actual = (PdxInstance) serializer.deserialize(bytes);
    assertEquals(original, actual);
  }
}
