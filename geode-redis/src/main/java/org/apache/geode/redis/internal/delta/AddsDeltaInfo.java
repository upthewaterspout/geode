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
 *
 */

package org.apache.geode.redis.internal.delta;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.geode.DataSerializer;
import org.apache.geode.internal.cache.RemoteEntryModification;
import org.apache.geode.internal.serialization.DataSerializableFixedID;
import org.apache.geode.internal.serialization.DeserializationContext;
import org.apache.geode.internal.serialization.KnownVersion;
import org.apache.geode.internal.serialization.SerializationContext;
import org.apache.geode.redis.internal.data.RedisHash;
import org.apache.geode.redis.internal.data.RedisKey;

public class AddsDeltaInfo implements DeltaInfo, RemoteEntryModification<RedisKey, RedisHash>,
    DataSerializableFixedID {
  private final List<byte[]> deltas;

  public AddsDeltaInfo(int size) {
    this(new ArrayList<>(size));
  }

  public AddsDeltaInfo(List<byte[]> deltas) {
    this.deltas = deltas;
  }

  public void add(byte[] delta) {
    deltas.add(delta);
  }

  public void serializeTo(DataOutput out) throws IOException {
    DataSerializer.writeEnum(DeltaType.ADDS, out);
    toData(out, null);
  }

  public List<byte[]> getAdds() {
    return deltas;
  }

  @Override
  public RedisHash apply(RedisKey redisKey, RedisHash redisHash) {
    if(redisHash == null) {
      redisHash = new RedisHash(this.deltas);
    }
    else {
      redisHash.hashPutFields(this.deltas);
    }

    redisHash.setDelta(this);
    return redisHash;
  }

  @Override
  public int getDSFID() {
    return REDIS_ADD_DELTAS;
  }

  @Override
  public void toData(DataOutput out, SerializationContext context) throws IOException {
    out.writeInt(deltas.size());
    for(byte[] delta: deltas) {
      DataSerializer.writeByteArray(delta, out);
    }

  }

  @Override
  public void fromData(DataInput in, DeserializationContext context)
      throws IOException, ClassNotFoundException {
    int length = in.readInt();
    ArrayList<byte[]> deltas = new ArrayList<>(length);
    for(int i =0 ;i < length; i++) {
      deltas.add(DataSerializer.readByteArray(in));
    }

  }

  @Override
  public KnownVersion[] getSerializationVersions() {
    return null;
  }
}
