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
package org.apache.geode.internal.protocol.protobuf.v1.operations;

import java.util.ArrayList;
import java.util.List;

import org.apache.geode.internal.protocol.protobuf.v1.BasicTypes.Struct;
import org.apache.geode.internal.protocol.protobuf.v1.ProtobufSerializationService;
import org.apache.geode.internal.protocol.protobuf.v1.serialization.exception.EncodingException;

public class OqlQueryUtils {

  public static List<Struct> expectedResults(ProtobufSerializationService serializer,
      Object[]... rows) throws EncodingException {
    List<Struct> results = new ArrayList<>();
    for (Object[] row : rows) {
      Struct.Builder structBuilder = Struct.newBuilder();
      for (Object element : row) {
        structBuilder.addElement(serializer.encode(element));
      }
      results.add(structBuilder.build());
    }

    return results;
  }
}
