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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.pdx.PdxInstanceFactory;
import org.apache.geode.pdx.internal.PdxInstanceFactoryImpl;
import org.apache.geode.pdx.internal.TypeRegistration;
import org.apache.geode.pdx.internal.TypeRegistry;

public class PdxInstanceFactoryMock {

  public static final PdxInstanceFactory createMockFactory(String className) {
    TypeRegistration registration = mock(TypeRegistration.class);
    InternalCache cache = mock(InternalCache.class);
    when(cache.getPdxRegistry()).thenReturn(new TypeRegistry(registration));

    return PdxInstanceFactoryImpl.newCreator(ProtobufStructSerializer.PROTOBUF_STRUCT, true, cache);
  }
}
