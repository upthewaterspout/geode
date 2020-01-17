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
package org.apache.geode.distributed.internal.membership.gms;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.geode.distributed.internal.membership.api.MemberIdentifier;
import org.apache.geode.distributed.internal.membership.api.MemberIdentifierBuilder;
import org.apache.geode.internal.serialization.Version;

/**
 * MemberDataBuilderImpl is the implementation of MemberDataBuilder. It constructs a
 * MemberData and is exposed to geode-core to support construction of identifiers in
 * deserialization code and in tests.
 */
public class MemberIdentifierBuilderImpl implements MemberIdentifierBuilder {

  private static final String EMPTY_STRING = "";

  private InetAddress inetAddress;
  private String hostName;
  private int membershipPort = -1;
  private int directChannelPort = -1;
  private int vmPid = -1;
  private int vmKind = MemberIdentifier.NORMAL_DM_TYPE;
  private int vmViewId = -1;
  private String name = EMPTY_STRING;
  private String[] groups;
  private String durableId;
  private int durableTimeout = -1;
  private boolean preferredForCoordinator = true;
  private boolean networkPartitionDetectionEnabled;
  private short versionOrdinal = Version.CURRENT_ORDINAL;
  private long uuidMostSignificantBits = 0;
  private long uuidLeastSignificantBits = 0;
  private boolean isPartial;
  private String uniqueTag;

  public void setMemberWeight(byte memberWeight) {
    this.memberWeight = memberWeight;
  }

  private byte memberWeight = 0;

  /**
   * Create a builder for the given host machine and host name
   */
  public static MemberIdentifierBuilderImpl newBuilder(InetAddress hostAddress, String hostName) {
    return new MemberIdentifierBuilderImpl(hostAddress, hostName);
  }

  /**
   * Create a builder for the machine hosting this process
   */
  public static MemberIdentifierBuilderImpl newBuilderForLocalHost(String hostName) {
    return new MemberIdentifierBuilderImpl(hostName);
  }

  public static MemberIdentifierBuilderImpl newBuilderForDeserialization() {
    return new MemberIdentifierBuilderImpl();
  }

  private MemberIdentifierBuilderImpl() {

  }

  private MemberIdentifierBuilderImpl(InetAddress hostAddress, String hostName) {
    inetAddress = hostAddress;
    this.hostName = hostName;
  }

  private MemberIdentifierBuilderImpl(String fakeHostName) {
    try {
      inetAddress = InetAddress.getLocalHost();
    } catch (UnknownHostException e2) {
      throw new RuntimeException("Unable to resolve local host address", e2);
    }
    hostName = fakeHostName;
  }

  public MemberIdentifierBuilderImpl setMembershipPort(int membershipPort) {
    this.membershipPort = membershipPort;
    return this;
  }

  public MemberIdentifierBuilderImpl setDirectChannelPort(int directChannelPort) {
    this.directChannelPort = directChannelPort;
    return this;
  }

  public MemberIdentifierBuilderImpl setVmPid(int vmPid) {
    this.vmPid = vmPid;
    return this;
  }

  public MemberIdentifierBuilderImpl setVmKind(int vmKind) {
    this.vmKind = vmKind;
    return this;
  }

  public MemberIdentifierBuilderImpl setVmViewId(int vmViewId) {
    this.vmViewId = vmViewId;
    return this;
  }

  public MemberIdentifierBuilderImpl setName(String name) {
    this.name = name;
    return this;
  }

  public MemberIdentifierBuilderImpl setGroups(String[] groups) {
    this.groups = groups;
    return this;
  }

  public MemberIdentifierBuilderImpl setDurableId(String durableId) {
    this.durableId = durableId;
    return this;
  }

  public MemberIdentifierBuilderImpl setDurableTimeout(int durableTimeout) {
    this.durableTimeout = durableTimeout;
    return this;
  }

  public MemberIdentifierBuilderImpl setPreferredForCoordinator(boolean preferredForCoordinator) {
    this.preferredForCoordinator = preferredForCoordinator;
    return this;
  }

  public MemberIdentifierBuilderImpl setNetworkPartitionDetectionEnabled(
      boolean networkPartitionDetectionEnabled) {
    this.networkPartitionDetectionEnabled = networkPartitionDetectionEnabled;
    return this;
  }

  public MemberIdentifierBuilderImpl setVersionOrdinal(short versionOrdinal) {
    this.versionOrdinal = versionOrdinal;
    return this;
  }

  public MemberIdentifierBuilderImpl setUuidMostSignificantBits(long uuidMostSignificantBits) {
    this.uuidMostSignificantBits = uuidMostSignificantBits;
    return this;
  }

  public MemberIdentifierBuilderImpl setUuidLeastSignificantBits(long uuidLeastSignificantBits) {
    this.uuidLeastSignificantBits = uuidLeastSignificantBits;
    return this;
  }

  public MemberIdentifierBuilderImpl setIsPartial(boolean partial) {
    this.isPartial = partial;
    return this;
  }

  @Override
  public MemberIdentifierBuilder setUniqueTag(String uniqueTag) {
    this.uniqueTag = uniqueTag;
    return this;
  }

  public MemberIdentifier build() {
    return new GMSMemberData(inetAddress, hostName,
        membershipPort, vmPid, (byte) vmKind, directChannelPort,
        vmViewId, name, groups, durableId, durableTimeout,
        networkPartitionDetectionEnabled, preferredForCoordinator, versionOrdinal,
        uuidMostSignificantBits, uuidLeastSignificantBits, memberWeight, isPartial, uniqueTag);
  }

}
