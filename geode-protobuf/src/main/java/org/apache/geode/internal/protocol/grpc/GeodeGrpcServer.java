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

import io.grpc.stub.StreamObserver;

import org.apache.geode.internal.protocol.protobuf.v1.GeodeGrpc;
import org.apache.geode.internal.protocol.protobuf.v1.ProtobufSerializationService;
import org.apache.geode.internal.protocol.protobuf.v1.RegionAPI;
import org.apache.geode.internal.protocol.protobuf.v1.authentication.AuthorizingCache;

public class GeodeGrpcServer extends GeodeGrpc.GeodeImplBase {


  public final AuthorizingCache cache;
  public ProtobufSerializationService serialization = new ProtobufSerializationService();

  public GeodeGrpcServer(AuthorizingCache cache) {
    this.cache = cache;
  }

  @Override
  public void put(RegionAPI.PutRequest request,
      StreamObserver<RegionAPI.PutResponse> responseObserver) {
    cache.put(request.getRegionName(), serialization.decode(request.getEntry().getKey()),
        serialization.decode(request.getEntry().getValue()));
    responseObserver.onNext(RegionAPI.PutResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void get(RegionAPI.GetRequest request,
      StreamObserver<RegionAPI.GetResponse> responseObserver) {
    Object value = cache.get(request.getRegionName(), serialization.decode(request.getKey()));
    responseObserver
        .onNext(RegionAPI.GetResponse.newBuilder().setResult(serialization.encode(value)).build());
    responseObserver.onCompleted();
  }

  @Override
  public void remove(RegionAPI.RemoveRequest request,
      StreamObserver<RegionAPI.RemoveResponse> responseObserver) {

    cache.remove(request.getRegionName(), serialization.decode(request.getKey()));
    responseObserver.onNext(RegionAPI.RemoveResponse.newBuilder().build());
    responseObserver.onCompleted();
  }
}
