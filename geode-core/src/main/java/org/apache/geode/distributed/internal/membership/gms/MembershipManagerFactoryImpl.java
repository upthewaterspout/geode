package org.apache.geode.distributed.internal.membership.gms;

import org.apache.geode.GemFireConfigException;
import org.apache.geode.SystemConnectException;
import org.apache.geode.distributed.internal.ClusterDistributionManager;
import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.distributed.internal.DistributionException;
import org.apache.geode.distributed.internal.membership.DistributedMembershipListener;
import org.apache.geode.distributed.internal.membership.InternalMembershipManager;
import org.apache.geode.distributed.internal.membership.adapter.GMSMembershipManager;
import org.apache.geode.distributed.internal.membership.gms.api.Authenticator;
import org.apache.geode.distributed.internal.membership.gms.api.MembershipManagerFactory;
import org.apache.geode.distributed.internal.membership.gms.api.MembershipStatistics;
import org.apache.geode.internal.admin.remote.RemoteTransportConfig;
import org.apache.geode.internal.tcp.ConnectionException;
import org.apache.geode.security.GemFireSecurityException;

public class MembershipManagerFactoryImpl implements MembershipManagerFactory {
  private final DistributedMembershipListener listener;
  private final RemoteTransportConfig transport;
  private MembershipStatistics statistics;
  private Authenticator authenticator;
  private final DistributionConfig config;
  private ClusterDistributionManager dm;

  public MembershipManagerFactoryImpl(DistributedMembershipListener listener,
      RemoteTransportConfig transport, DistributionConfig config,
      ClusterDistributionManager dm) {

    this.listener = listener;
    this.transport = transport;
    this.config = config;
    this.dm = dm;
  }

  @Override
  public MembershipManagerFactory setAuthenticator(Authenticator authenticator) {
    this.authenticator = authenticator;
    return this;
  }

  @Override
  public MembershipManagerFactory setStatistics(MembershipStatistics statistics) {
    this.statistics = statistics;
    return this;
  }

  @Override
  public InternalMembershipManager create() {
    GMSMembershipManager gmsMembershipManager = new GMSMembershipManager(listener, dm);
    Services services1 =
        new Services(gmsMembershipManager.getGMSManager(), transport, statistics, authenticator,
            config);
    try {
      services1.init();
      services1.start();
    } catch (ConnectionException e) {
      throw new DistributionException(
          "Unable to create membership manager",
          e);
    } catch (GemFireConfigException | SystemConnectException | GemFireSecurityException e) {
      throw e;
    } catch (RuntimeException e) {
      Services.getLogger().error("Unexpected problem starting up membership services", e);
      throw new SystemConnectException("Problem starting up membership services", e);
    }
    return gmsMembershipManager;
  }
}
