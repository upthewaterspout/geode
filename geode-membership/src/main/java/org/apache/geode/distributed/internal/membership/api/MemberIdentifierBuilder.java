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
package org.apache.geode.distributed.internal.membership.api;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.geode.distributed.internal.membership.gms.MemberIdentifierBuilderImpl;

/**
 * Membership identifiers must hold a MemberData object. This builder let's you create
 * one.<br>
 * See {@link MemberIdentifierFactory}<br>
 */
public interface MemberIdentifierBuilder {

  /**
   * Create a builder for the given host machine and host name
   */
  static MemberIdentifierBuilder newBuilder(InetAddress hostAddress, String hostName) {
    return MemberIdentifierBuilderImpl.newBuilder(hostAddress, hostName);
  }

  /**
   * Create a builder for the machine hosting this process
   */
  static MemberIdentifierBuilder newBuilderForLocalHost(String hostName) {
    return MemberIdentifierBuilderImpl.newBuilderForLocalHost(hostName);
  }

  /**
   * Create a builder for a MemberIdentifier to deserialize itself
   */
  static MemberIdentifierBuilder newBuilderForDeserialization() {
    return MemberIdentifierBuilderImpl.newBuilderForDeserialization();
  }

  /** Parses comma-separated-roles/groups into array of groups (strings). */
  static String[] parseGroups(String csvRoles, String csvGroups) {
    List<String> groups = new ArrayList<String>();
    parseCsv(groups, csvRoles);
    parseCsv(groups, csvGroups);
    return groups.toArray(new String[groups.size()]);
  }

  static void parseCsv(List<String> groups, String csv) {
    if (csv == null || csv.length() == 0) {
      return;
    }
    StringTokenizer st = new StringTokenizer(csv, ",");
    while (st.hasMoreTokens()) {
      String groupName = st.nextToken().trim();
      if (!groups.contains(groupName)) { // only add each group once
        groups.add(groupName);
      }
    }
  }

  MemberIdentifierBuilder setMembershipPort(int membershipPort);

  MemberIdentifierBuilder setDirectChannelPort(int directChannelPort);

  MemberIdentifierBuilder setVmPid(int vmPid);

  MemberIdentifierBuilder setVmKind(int vmKind);

  MemberIdentifierBuilder setVmViewId(int vmViewId);

  MemberIdentifierBuilder setName(String name);

  MemberIdentifierBuilder setGroups(String[] groups);

  MemberIdentifierBuilder setDurableId(String durableId);

  MemberIdentifierBuilder setDurableTimeout(int durableTimeout);

  MemberIdentifierBuilder setPreferredForCoordinator(boolean preferredForCoordinator);

  MemberIdentifierBuilder setNetworkPartitionDetectionEnabled(
      boolean networkPartitionDetectionEnabled);

  MemberIdentifierBuilder setVersionOrdinal(short versionOrdinal);

  MemberIdentifierBuilder setUuidMostSignificantBits(long uuidMostSignificantBits);

  MemberIdentifierBuilder setUuidLeastSignificantBits(long uuidLeastSignificantBits);

  MemberIdentifierBuilder setIsPartial(boolean isPartial);

  MemberIdentifierBuilder setUniqueTag(String uniqueTag);

  MemberIdentifier build();

}
