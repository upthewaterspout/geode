package org.apache.geode.distributed.internal.membership.gms;

import org.apache.geode.distributed.internal.DMStats;
import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.distributed.internal.membership.DistributedMembershipListener;
import org.apache.geode.distributed.internal.membership.InternalMembershipManager;
import org.apache.geode.distributed.internal.membership.gms.api.Authenticator;
import org.apache.geode.distributed.internal.membership.gms.api.MembershipManagerFactory;
import org.apache.geode.internal.admin.remote.RemoteTransportConfig;

public class MembershipManagerFactoryImpl implements MembershipManagerFactory {
  private final DistributedMembershipListener listener;
  private final RemoteTransportConfig transport;
  private final DMStats stats;
  private Authenticator authenticator;
  private final DistributionConfig config;

  public MembershipManagerFactoryImpl(DistributedMembershipListener listener,
      RemoteTransportConfig transport, DMStats stats, DistributionConfig config) {

    this.listener = listener;
    this.transport = transport;
    this.stats = stats;
    this.config = config;
  }

  @Override
  public MembershipManagerFactory setAuthenticator(Authenticator authenticator) {
    this.authenticator = authenticator;
    return this;
  }

  @Override
  public InternalMembershipManager create() {
    return services.newMembershipManager(listener, transport, stats, authenticator, config);
  }
}
