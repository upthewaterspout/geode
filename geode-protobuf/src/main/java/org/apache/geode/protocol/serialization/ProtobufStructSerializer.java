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
import com.google.protobuf.UnsafeByteOperations;

import org.apache.geode.cache.Cache;
import org.apache.geode.internal.InternalDataSerializer;
import org.apache.geode.internal.protocol.protobuf.v1.BasicTypes;
import org.apache.geode.internal.protocol.protobuf.v1.Struct;
import org.apache.geode.internal.protocol.protobuf.v1.Value;
import org.apache.geode.internal.tcp.ByteBufferInputStream;
import org.apache.geode.pdx.PdxInstance;
import org.apache.geode.pdx.PdxInstanceFactory;
import org.apache.geode.pdx.internal.PdxField;
import org.apache.geode.pdx.internal.PdxInstanceImpl;

public class ProtobufStructSerializer implements ValueSerializer {
  static final String PROTOBUF_STRUCT = "__PROTOBUF_STRUCT_AS_PDX";
  private Cache cache;

  @Override
  public ByteString serialize(Object object) throws IOException {
    PdxInstance pdxInstance = (PdxInstance) object;
    PdxInstanceImpl impl = (PdxInstanceImpl) pdxInstance;

    Struct.Builder structBuilder = Struct.newBuilder();
    for (String fieldName : pdxInstance.getFieldNames()) {
      Value.Builder builder = Value.newBuilder();
      PdxField pdxField = impl.getPdxType().getPdxField(fieldName);
      switch (pdxField.getFieldType()) {
        case STRING:
          builder.setEncodedValue(BasicTypes.EncodedValue.newBuilder()
              .setStringResult((String) pdxInstance.getField(fieldName)).build());
          break;
        case BOOLEAN:
          builder.setEncodedValue(BasicTypes.EncodedValue.newBuilder()
              .setBooleanResult((Boolean) pdxInstance.getField(fieldName)).build());
          break;
        case INT:
          builder.setEncodedValue(BasicTypes.EncodedValue.newBuilder()
              .setIntResult((Integer) pdxInstance.getField(fieldName)).build());
          break;
        case BYTE:
          builder.setEncodedValue(BasicTypes.EncodedValue.newBuilder()
              .setByteResult((Byte) pdxInstance.getField(fieldName)).build());
          break;
        case LONG:
          builder.setEncodedValue(BasicTypes.EncodedValue.newBuilder()
              .setLongResult((Long) pdxInstance.getField(fieldName)).build());
          break;
        case BYTE_ARRAY:
          builder
              .setEncodedValue(BasicTypes.EncodedValue.newBuilder()
                  .setBinaryResult(createByteString(impl.getRaw(pdxField.getFieldIndex()))))
              .build();
          break;
        default:
          throw new IllegalStateException("Don't know how to translate object of type "
              + pdxField.getFieldType() + ": " + pdxInstance.getField(fieldName));
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

  private ByteString createByteString(ByteBufferInputStream.ByteSource raw) throws IOException {
    int length = InternalDataSerializer
        .readArrayLength(new ByteBufferInputStream(raw.getBackingByteBuffer()));
    return UnsafeByteOperations.unsafeWrap(raw.getBackingByteBuffer());
  }

}
