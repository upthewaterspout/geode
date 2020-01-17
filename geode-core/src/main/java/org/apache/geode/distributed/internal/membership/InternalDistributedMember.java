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
package org.apache.geode.distributed.internal.membership;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jgroups.util.UUID;

import org.apache.geode.InternalGemFireError;
import org.apache.geode.annotations.VisibleForTesting;
import org.apache.geode.annotations.internal.MutableForTesting;
import org.apache.geode.cache.client.ServerConnectivityException;
import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.distributed.DurableClientAttributes;
import org.apache.geode.distributed.Role;
import org.apache.geode.distributed.internal.DistributionAdvisor.ProfileId;
import org.apache.geode.distributed.internal.ServerLocation;
import org.apache.geode.distributed.internal.membership.api.MemberIdentifier;
import org.apache.geode.distributed.internal.membership.api.MemberIdentifierBuilder;
import org.apache.geode.internal.cache.versions.VersionSource;
import org.apache.geode.internal.inet.LocalHostUtil;
import org.apache.geode.internal.net.SocketCreator;
import org.apache.geode.internal.serialization.DeserializationContext;
import org.apache.geode.internal.serialization.SerializationContext;
import org.apache.geode.internal.serialization.Version;
import org.apache.geode.logging.internal.OSProcess;

/**
 * This is the fundamental representation of a member of a GemFire distributed system.
 */
public class InternalDistributedMember
    implements DistributedMember, Externalizable, ProfileId, VersionSource<DistributedMember>,
    MemberIdentifier {
  private static final long serialVersionUID = -2785249969777296507L;

  /** Retrieves an InetAddress given the provided hostname */
  @MutableForTesting
  protected static HostnameResolver hostnameResolver =
      (location) -> InetAddress.getByName(location.getHostName());
  private final MemberIdentifier delegate;

  /** lock object used when getting/setting roles/rolesSet fields */

  // Used only by deserialization
  public InternalDistributedMember() {
    this.delegate = MemberIdentifierBuilder.newBuilderForDeserialization().build();
  }

  /**
   * Construct a InternalDistributedMember
   * <p>
   *
   * This, and the following constructor are the only valid ways to create an ID for a distributed
   * member for use in the P2P cache. Use of other constructors can break
   * network-partition-detection.
   *
   * @param i the inet address
   * @param membershipPort the membership port
   * @param splitBrainEnabled whether this feature is enabled for the member
   * @param canBeCoordinator whether the member is eligible to be the membership coordinator
   */
  public InternalDistributedMember(InetAddress i, int membershipPort, boolean splitBrainEnabled,
      boolean canBeCoordinator) {

    this.delegate = MemberIdentifierBuilder.newBuilder(i, getHostName(i))
        .setMembershipPort(membershipPort)
        .setNetworkPartitionDetectionEnabled(splitBrainEnabled)
        .setPreferredForCoordinator(canBeCoordinator)
        .build();
  }

  private static String getHostName(InetAddress i) {
    return SocketCreator.resolve_dns ? SocketCreator.getHostName(i) : i.getHostAddress();
  }

  /**
   * Construct a InternalDistributedMember based on the given member data.
   *
   */
  public InternalDistributedMember(MemberIdentifier m) {
    this.delegate = m;

    if (delegate.getHostName() == null || delegate.isPartial()) {
      String hostName = getHostName(m.getInetAddress());
      delegate.setHostName(hostName);
    }
  }

  /**
   * Create a InternalDistributedMember referring to the current host (as defined by the given
   * string).
   * <p>
   *
   * <b> THIS METHOD IS FOR TESTING ONLY. DO NOT USE IT TO CREATE IDs FOR USE IN THE PRODUCT. IT
   * DOES NOT PROPERLY INITIALIZE ATTRIBUTES NEEDED FOR P2P FUNCTIONALITY. </b>
   *
   *
   * @param i the hostname, stored in the member ID but not resolved - local host inet addr is used
   * @param p the membership listening port
   * @throws RuntimeException if the given hostname cannot be resolved
   */
  @VisibleForTesting
  public InternalDistributedMember(String i, int p) {
    delegate = MemberIdentifierBuilder.newBuilderForLocalHost(i)
        .setMembershipPort(p)
        .build();
  }

  /**
   * Creates a new InternalDistributedMember for use in notifying listeners in client
   * caches. The version information in the ID is set to Version.CURRENT.
   *
   * @param location the coordinates of the server
   */

  public InternalDistributedMember(ServerLocation location) {
    delegate = MemberIdentifierBuilder.newBuilder(getInetAddress(location), location.getHostName())
        .setMembershipPort(location.getPort())
        .setNetworkPartitionDetectionEnabled(false)
        .setPreferredForCoordinator(true)
        .build();
  }

  private static InetAddress getInetAddress(ServerLocation location) {
    final InetAddress addr;
    try {
      addr = hostnameResolver.getInetAddress(location);
    } catch (UnknownHostException e) {
      throw new ServerConnectivityException("Unable to resolve server location " + location, e);
    }
    return addr;
  }

  /**
   * Create a InternalDistributedMember referring to the current host (as defined by the given
   * string) with additional info including optional connection name and an optional unique string.
   * Currently these two optional fields (and this constructor) are only used by the
   * LonerDistributionManager.
   * <p>
   *
   * < b> DO NOT USE THIS METHOD TO CREATE ANYTHING OTHER THAN A LONER ID. IT DOES NOT PROPERLY
   * INITIALIZE THE ID. </b>
   *
   * @param host the hostname, must be for the current host
   * @param p the membership port
   * @param n member name
   * @param u unique string used make the member more unique
   * @param vmKind the dmType
   * @param groups the server groups / roles
   * @param attr durable client attributes, if any
   *
   * @throws UnknownHostException if the given hostname cannot be resolved
   */
  public InternalDistributedMember(String host, int p, String n, String u, int vmKind,
      String[] groups, DurableClientAttributes attr) throws UnknownHostException {
    delegate = createMemberData(host, p, n, vmKind, groups, attr, u);

    defaultToCurrentHost();
  }

  private static MemberIdentifier createMemberData(String host, int p, String n, int vmKind,
      String[] groups,
      DurableClientAttributes attr, String u) {
    InetAddress addr = LocalHostUtil.toInetAddress(host);
    MemberIdentifierBuilder builder = MemberIdentifierBuilder.newBuilder(addr, host)
        .setName(n)
        .setMembershipPort(p)
        .setDirectChannelPort(p)
        .setPreferredForCoordinator(false)
        .setNetworkPartitionDetectionEnabled(true)
        .setVmKind(vmKind)
        .setUniqueTag(u)
        .setGroups(groups);
    if (attr != null) {
      builder.setDurableId(attr.getId())
          .setDurableTimeout(attr.getTimeout());
    }
    return builder.build();
  }

  /**
   * Create a InternalDistributedMember
   * <p>
   *
   * <b> THIS METHOD IS FOR TESTING ONLY. DO NOT USE IT TO CREATE IDs FOR USE IN THE PRODUCT. IT
   * DOES NOT PROPERLY INITIALIZE ATTRIBUTES NEEDED FOR P2P FUNCTIONALITY. </b>
   *
   *
   * @param i the host address
   * @param p the membership listening port
   */
  public InternalDistributedMember(InetAddress i, int p) {
    delegate = MemberIdentifierBuilder.newBuilder(i, "localhost")
        .setMembershipPort(p)
        .build();
    defaultToCurrentHost();
  }

  /**
   * Create a InternalDistributedMember as defined by the given address.
   * <p>
   *
   * <b> THIS METHOD IS FOR TESTING ONLY. DO NOT USE IT TO CREATE IDs FOR USE IN THE PRODUCT. IT
   * DOES NOT PROPERLY INITIALIZE ATTRIBUTES NEEDED FOR P2P FUNCTIONALITY. </b>
   *
   * @param addr address of the server
   * @param p the listening port of the server
   * @param isCurrentHost true if the given host refers to the current host (bridge and gateway use
   *        false to create a temporary id for the OTHER side of a connection)
   */
  public InternalDistributedMember(InetAddress addr, int p, boolean isCurrentHost) {
    delegate = MemberIdentifierBuilder.newBuilder(addr, "localhost")
        .setMembershipPort(p).build();
    if (isCurrentHost) {
      defaultToCurrentHost();
    }
  }


  /** this reads an ID written with writeEssentialData */
  public static InternalDistributedMember readEssentialData(DataInput in)
      throws IOException, ClassNotFoundException {
    final InternalDistributedMember mbr = new InternalDistributedMember();
    mbr._readEssentialData(in, InternalDistributedMember::getHostName);
    return mbr;
  }

  public static void setHostnameResolver(final HostnameResolver hostnameResolver) {
    InternalDistributedMember.hostnameResolver = hostnameResolver;
  }

  /**
   * Returns this client member's durable attributes or null if no durable attributes were created.
   */
  public DurableClientAttributes getDurableClientAttributes() {
    assert !this.isPartial();
    String durableId = getDurableId();
    if (durableId == null || durableId.isEmpty()) {
      return new DurableClientAttributes("", 300);
    }
    return new DurableClientAttributes(durableId, delegate.getDurableTimeout());
  }

  /**
   * Returns an unmodifiable Set of this member's Roles.
   */
  public Set<Role> getRoles() {

    if (delegate.getGroups() == null) {
      return Collections.emptySet();
    }
    return getGroups().stream().map(InternalRole::getRole).collect(Collectors.toSet());
  }

  public int compareTo(DistributedMember o) {
    return compareTo(o, false, true);
  }

  public int compareTo(DistributedMember o, boolean compareMemberData, boolean compareViewIds) {
    if (this == o) {
      return 0;
    }
    // obligatory type check
    if (!(o instanceof InternalDistributedMember))
      throw new ClassCastException(
          "InternalDistributedMember.compareTo(): comparison between different classes");
    MemberIdentifier other = (MemberIdentifier) o;

    return compareTo(other, compareMemberData, compareViewIds);
  }

  protected void defaultToCurrentHost() {
    delegate.setProcessId(OSProcess.getId());
    try {
      if (SocketCreator.resolve_dns) {
        delegate.setHostName(SocketCreator.getHostName(LocalHostUtil.getLocalHost()));
      } else {
        delegate.setHostName(LocalHostUtil.getLocalHost().getHostAddress());
      }
    } catch (UnknownHostException ee) {
      throw new InternalGemFireError(ee);
    }
  }

  @Override
  public String getHostName() {
    return delegate.getHostName();
  }

  @Override
  public InetAddress getInetAddress() {
    return delegate.getInetAddress();
  }

  @Override
  public int getMembershipPort() {
    return delegate.getMembershipPort();
  }

  @Override
  public String getUniqueId() {
    return delegate.getUniqueId();
  }

  @Override
  public Version getVersionObject() {
    return delegate.getVersionObject();
  }

  @Override
  public short getVersionOrdinal() {
    return delegate.getVersionOrdinal();
  }

  @Override
  public int getVmViewId() {
    return delegate.getVmViewId();
  }

  @Override
  public boolean preferredForCoordinator() {
    return delegate.preferredForCoordinator();
  }

  @Override
  public int getVmKind() {
    return delegate.getVmKind();
  }

  @Override
  public byte getMemberWeight() {
    return delegate.getMemberWeight();
  }

  @Override
  public List<String> getGroups() {
    return delegate.getGroups();
  }

  @Override
  public void setVmViewId(int viewNumber) {
    delegate.setVmViewId(viewNumber);
  }

  @Override
  public void setPreferredForCoordinator(boolean preferred) {
    delegate.setPreferredForCoordinator(preferred);
  }

  @Override
  public void setDirectChannelPort(int dcPort) {
    delegate.setDirectChannelPort(dcPort);
  }

  @Override
  public void setVmKind(int dmType) {
    delegate.setVmKind(dmType);
  }

  @Override
  public String getHost() {
    return delegate.getHost();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public void setAll(MemberIdentifier memberData) {
    this.delegate.setAll(memberData);
  }

  @Override
  public void setIsPartial(boolean b) {
    delegate.setIsPartial(b);
  }

  @Override
  public boolean isPartial() {
    return delegate.isPartial();
  }

  @Override
  public String getDurableId() {
    return delegate.getDurableId();
  }

  @Override
  public int getDurableTimeout() {
    return delegate.getDurableTimeout();
  }

  @Override
  public Version getVersion() {
    return delegate.getVersion();
  }

  @Override
  public String getUniqueTag() {
    return delegate.getUniqueTag();
  }

  @Override
  public void setVersionOrdinal(short versionOrdinal) {
    delegate.setVersionOrdinal(versionOrdinal);
  }

  @Override
  public void setUUID(UUID u) {
    delegate.setUUID(u);
  }

  @Override
  public UUID getUUID() {
    return delegate.getUUID();
  }

  @Override
  public long getUuidMostSignificantBits() {
    return delegate.getUuidMostSignificantBits();
  }

  @Override
  public long getUuidLeastSignificantBits() {
    return delegate.getUuidLeastSignificantBits();
  }

  @Override
  public boolean isNetworkPartitionDetectionEnabled() {
    return delegate.isNetworkPartitionDetectionEnabled();
  }

  @Override
  public InetAddress getInetAddr() {
    return delegate.getInetAddr();
  }

  @Override
  public int getProcessId() {
    return delegate.getProcessId();
  }

  @Override
  public int getDirectChannelPort() {
    return delegate.getDirectChannelPort();
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public void setMembershipPort(int udpPort) {
    delegate.setMembershipPort(udpPort);
  }

  @Override
  public void setNetworkPartitionDetectionEnabled(boolean networkPartitionDetectionEnabled) {
    delegate.setNetworkPartitionDetectionEnabled(networkPartitionDetectionEnabled);
  }

  @Override
  public void setMemberWeight(byte memberWeight) {
    delegate.setMemberWeight(memberWeight);
  }

  @Override
  public void setInetAddr(InetAddress inetAddr) {
    delegate.setInetAddr(inetAddr);
  }

  @Override
  public void setProcessId(int processId) {
    delegate.setProcessId(processId);
  }

  @Override
  public void setVersion(Version v) {
    delegate.setVersion(v);
  }

  @Override
  public void setName(String name) {
    delegate.setName(name);
  }

  @Override
  public void setGroups(String[] groups) {
    delegate.setGroups(groups);
  }

  @Override
  public void addFixedToString(StringBuilder sb, boolean useIpAddress) {
    delegate.addFixedToString(sb, useIpAddress);
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    delegate.writeExternal(out);
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    delegate.readExternal(in);
  }

  @Override
  public void toDataPre_GFE_9_0_0_0(DataOutput out,
      SerializationContext context) throws IOException {
    delegate.toDataPre_GFE_9_0_0_0(out, context);
  }

  @Override
  public void toDataPre_GFE_7_1_0_0(DataOutput out,
      SerializationContext context) throws IOException {
    delegate.toDataPre_GFE_7_1_0_0(out, context);
  }

  @Override
  public void fromDataPre_GFE_9_0_0_0(DataInput in,
      DeserializationContext context)
      throws IOException, ClassNotFoundException {
    delegate.fromDataPre_GFE_9_0_0_0(in, context);
  }

  @Override
  public void fromDataPre_GFE_7_1_0_0(DataInput in,
      DeserializationContext context)
      throws IOException, ClassNotFoundException {
    delegate.fromDataPre_GFE_7_1_0_0(in, context);
  }

  @Override
  public void _readEssentialData(DataInput in,
      Function<InetAddress, String> hostnameResolver)
      throws IOException, ClassNotFoundException {
    delegate._readEssentialData(in, hostnameResolver);
  }

  @Override
  public void writeEssentialData(DataOutput out) throws IOException {
    delegate.writeEssentialData(out);
  }

  @Override
  public boolean hasUUID() {
    return delegate.hasUUID();
  }

  @Override
  public void setHostName(String hostName) {
    delegate.setHostName(hostName);
  }

  @Override
  public void setDurableTimeout(int newValue) {
    delegate.setDurableTimeout(newValue);
  }

  @Override
  public void setDurableId(String id) {
    delegate.setDurableId(id);
  }

  @Override
  public void writeEssentialData(DataOutput out,
      SerializationContext context) throws IOException {
    delegate.writeEssentialData(out, context);
  }

  @Override
  public void readEssentialData(DataInput in,
      DeserializationContext context)
      throws IOException, ClassNotFoundException {
    delegate.readEssentialData(in, context);
  }

  @Override
  public boolean hasAdditionalData() {
    return delegate.hasAdditionalData();
  }

  @Override
  public void writeAdditionalData(DataOutput out) throws IOException {
    delegate.writeAdditionalData(out);
  }

  @Override
  public void readAdditionalData(DataInput in) throws ClassNotFoundException, IOException {
    delegate.readAdditionalData(in);
  }

  @Override
  public int compareWith(MemberIdentifier o) {
    return delegate.compareWith(o);
  }

  @Override
  public int compareTo(MemberIdentifier o, boolean compareUUIDs) {
    return delegate.compareTo(o, compareUUIDs);
  }

  @Override
  public int compareTo(MemberIdentifier o, boolean compareUUIDs, boolean compareViewIds) {
    return delegate.compareTo(o, compareUUIDs, compareViewIds);
  }

  @Override
  public int compareAdditionalData(
      MemberIdentifier his) {
    return delegate.compareAdditionalData(his);
  }

  @Override
  public int getVmPid() {
    return delegate.getVmPid();
  }

  @Override
  public void setUniqueTag(String tag) {
    delegate.setUniqueTag(tag);
  }

  @Override
  public int getDSFID() {
    return delegate.getDSFID();
  }

  @Override
  public void toData(DataOutput out,
      SerializationContext context) throws IOException {
    delegate.toData(out, context);
  }

  @Override
  public void fromData(DataInput in,
      DeserializationContext context) throws IOException, ClassNotFoundException {
    delegate.fromData(in, context);
  }

  @Override
  public Version[] getSerializationVersions() {
    return delegate.getSerializationVersions();
  }

  @FunctionalInterface
  public interface HostnameResolver {
    InetAddress getInetAddress(ServerLocation location) throws UnknownHostException;
  }
}
