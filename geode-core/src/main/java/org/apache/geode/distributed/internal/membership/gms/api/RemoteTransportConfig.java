package org.apache.geode.distributed.internal.membership.gms.api;

import java.util.Set;

import org.apache.geode.distributed.internal.membership.gms.messenger.MembershipInformation;
import org.apache.geode.internal.admin.SSLConfig;
import org.apache.geode.internal.admin.TransportConfig;
import org.apache.geode.internal.admin.remote.DistributionLocatorId;

public interface RemoteTransportConfig extends TransportConfig {
  Set getIds();

  boolean isMcastEnabled();

  DistributionLocatorId getMcastId();

  int getVmKind();

  boolean isTcpDisabled();

  String getBindAddress();

  SSLConfig getSSLConfig();

  String getMembershipPortRange();

  int getTcpPort();

  boolean getIsReconnectingDS();

  void setIsReconnectingDS(boolean isReconnectingDS);

  MembershipInformation getOldDSMembershipInfo();

  void setOldDSMembershipInfo(MembershipInformation oldDSMembershipInfo);

  String locatorsString();
}
