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
package org.apache.geode.internal.protocol.grpc;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.protocol.protobuf.v1.BasicTypes;
import org.apache.geode.internal.protocol.protobuf.v1.GeodeGrpc;
import org.apache.geode.internal.protocol.protobuf.v1.RegionAPI;
import org.apache.geode.internal.protocol.protobuf.v1.authentication.AuthorizingCache;
import org.apache.geode.internal.protocol.protobuf.v1.authentication.AuthorizingCacheImpl;
import org.apache.geode.internal.protocol.protobuf.v1.authentication.NoSecurityAuthorizer;

public class GeodeGrpcServerIntegrationTest {

  private GeodeGrpc.GeodeBlockingStub stub;
  private Server server;
  private ManagedChannel channel;
  private int port;

  @Before
  public void setupServer() throws IOException {
    port = AvailablePortHelper.getRandomAvailableTCPPort();
    InternalCache geodeCache = (InternalCache) new CacheFactory().create();;
    geodeCache.createRegionFactory(RegionShortcut.REPLICATE).create("region");
    AuthorizingCache cache = new AuthorizingCacheImpl(geodeCache, new NoSecurityAuthorizer());
    server = ServerBuilder.forPort(port).addService(new GeodeGrpcServer(cache)).build().start();
    channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();

    stub = GeodeGrpc.newBlockingStub(channel);
  }

  @After
  public void shutdown() {
    channel.shutdown();
    server.shutdown();
  }

  public BasicTypes.EncodedValue encode(String value) {
    return BasicTypes.EncodedValue.newBuilder().setStringResult("key").build();
  }

  @Test
  public void testCrud() {
    put();

    RegionAPI.GetResponse getResponse = get();
    assertEquals(encode("value"), getResponse.getResult());

    getResponse = get();
    assertEquals(encode("value"), getResponse.getResult());
  }

  private RegionAPI.GetResponse get() {
    return stub.get(
        RegionAPI.GetRequest.newBuilder().setRegionName("region").setKey(encode("key")).build());
  }

  private void put() {
    RegionAPI.PutResponse response = stub.put(RegionAPI.PutRequest.newBuilder()
        .setRegionName("region")
        .setEntry(
            BasicTypes.Entry.newBuilder().setKey(encode("key")).setValue(encode("value")).build())
        .build());
  }
}
