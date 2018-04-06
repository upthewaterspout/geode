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

import org.apache.geode.cache.Cache;
import org.apache.geode.internal.protocol.protobuf.v1.BasicTypes;
import org.apache.geode.internal.protocol.protobuf.v1.Struct;
import org.apache.geode.internal.protocol.protobuf.v1.Value;
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
      Value.Builder builder = Value.newBuilder();
      if (value instanceof String) {
        builder.setEncodedValue(
            BasicTypes.EncodedValue.newBuilder().setStringResult((String) value).build());
      } else if (value instanceof Boolean) {
        builder.setEncodedValue(
            BasicTypes.EncodedValue.newBuilder().setBooleanResult((Boolean) value).build());
      } else if (value instanceof Integer) {
        builder.setEncodedValue(
            BasicTypes.EncodedValue.newBuilder().setIntResult((Integer) value).build());
      } else if (value instanceof Byte) {
        builder.setEncodedValue(
            BasicTypes.EncodedValue.newBuilder().setByteResult((Byte) value).build());
      } else if (value instanceof Long) {
        builder.setEncodedValue(
            BasicTypes.EncodedValue.newBuilder().setLongResult((Long) value).build());
      } else if (value instanceof byte[]) {
        builder.setEncodedValue(BasicTypes.EncodedValue.newBuilder()
            .setBinaryResult(ByteString.copyFrom((byte[]) value)).build());
      } else {
        throw new IllegalStateException(
            "Don't know how to translate object of type " + value.getClass() + ": " + value);
      }
      structBuilder.putFields(fieldName, builder.build());
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
      switch (value.getKindCase()) {
        case ENCODEDVALUE:
          switch (value.getEncodedValue().getValueCase()) {
            case STRINGRESULT:
              pdxInstanceFactory.writeString(fieldName, value.getEncodedValue().getStringResult());
              break;
            case BOOLEANRESULT:
              pdxInstanceFactory.writeBoolean(fieldName,
                  value.getEncodedValue().getBooleanResult());
              break;
            case INTRESULT:
              pdxInstanceFactory.writeInt(fieldName, value.getEncodedValue().getIntResult());
              break;
            case BYTERESULT:
              pdxInstanceFactory.writeByte(fieldName,
                  (byte) value.getEncodedValue().getByteResult());
              break;
            case LONGRESULT:
              pdxInstanceFactory.writeLong(fieldName, value.getEncodedValue().getLongResult());
              break;
            case BINARYRESULT:
              pdxInstanceFactory.writeByteArray(fieldName,
                  value.getEncodedValue().getBinaryResult().toByteArray());
              break;
            default:
              throw new IllegalStateException("Don't know how to translate object of type "
                  + value.getEncodedValue().getValueCase() + ": " + value);
          }
          break;

        default:
          throw new IllegalStateException(
              "Don't know how to translate object of type " + value.getKindCase() + ": " + value);
      }
    }

    return pdxInstanceFactory.create();
  }

  @Override
  public void init(Cache cache) {
    this.cache = cache;

  }
}
