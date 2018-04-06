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

import java.io.IOException;
import java.util.Map;

import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

import org.apache.geode.cache.Cache;
import org.apache.geode.pdx.PdxInstance;
import org.apache.geode.pdx.PdxInstanceFactory;

public class ProtobufStructSerializer implements ValueSerializer {
  static final String PROTOBUF_STRUCT = "__PROTOBUF_STRUCT_AS_PDX";
  private Cache cache;

  @Override
  public ByteString serialize(Object object) throws IOException {
    PdxInstance pdxInstance = (PdxInstance) object;

    Struct.Builder structBuilder = Struct.newBuilder();
    for (String fieldName : pdxInstance.getFieldNames()) {
      Object value = pdxInstance.getField(fieldName);
      structBuilder.putFields(fieldName, Value.newBuilder().setStringValue((String) value).build());
    }

    return structBuilder.build().toByteString();
  }

  @Override
  public Object deserialize(ByteString bytes) throws IOException, ClassNotFoundException {
    Struct struct = Struct.parseFrom(bytes);
    PdxInstanceFactory pdxInstanceFactory = cache.createPdxInstanceFactory(PROTOBUF_STRUCT);

    for (Map.Entry<String, Value> field : struct.getFieldsMap().entrySet()) {
      String fieldName = field.getKey();
      Value value = field.getValue();
      pdxInstanceFactory.writeString(fieldName, value.getStringValue());
    }

    return pdxInstanceFactory.create();
  }

  @Override
  public void init(Cache cache) {
    this.cache = cache;

  }

  @Override
  public String getID() {
    return null;
  }
}
