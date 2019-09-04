package org.apache.geode.distributed.internal.membership.gms.api;

import org.apache.geode.distributed.internal.DistributionConfig;

public interface MembershipConfig {
  boolean isReconnecting();

  int getLocatorWaitTime();

  long getJoinTimeout();

  int[] getMembershipPortRange();

  long getMemberTimeout();

  int getLossThreshold();

  int getMemberWeight();

  boolean isMulticastEnabled();

  boolean isNetworkPartitionDetectionEnabled();

  boolean isUDPSecurityEnabled();

  boolean areLocatorsPreferredAsCoordinators();

  DistributionConfig getDistributionConfig();

  RemoteTransportConfig getTransport();

  void setIsReconnecting(boolean b);
}
