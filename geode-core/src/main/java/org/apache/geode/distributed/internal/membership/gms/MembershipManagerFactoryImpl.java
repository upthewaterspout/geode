package org.apache.geode.distributed.internal.membership.gms;

import org.apache.geode.GemFireConfigException;
import org.apache.geode.SystemConnectException;
import org.apache.geode.distributed.internal.ClusterDistributionManager;
import org.apache.geode.distributed.internal.DistributionException;
import org.apache.geode.distributed.internal.membership.InternalMembershipManager;
import org.apache.geode.distributed.internal.membership.adapter.GMSMembershipManager;
import org.apache.geode.distributed.internal.membership.gms.api.Authenticator;
import org.apache.geode.distributed.internal.membership.gms.api.MembershipConfig;
import org.apache.geode.distributed.internal.membership.gms.api.MembershipListener;
import org.apache.geode.distributed.internal.membership.gms.api.MembershipManagerFactory;
import org.apache.geode.distributed.internal.membership.gms.api.MembershipStatistics;
import org.apache.geode.distributed.internal.membership.gms.api.MessageListener;
import org.apache.geode.internal.tcp.ConnectionException;
import org.apache.geode.security.GemFireSecurityException;

public class MembershipManagerFactoryImpl implements MembershipManagerFactory {
  private MembershipListener membershipListener;
  private MessageListener messageListener;
  private MembershipStatistics statistics;
  private Authenticator authenticator;
  private MembershipConfig config;
  private ClusterDistributionManager dm;

  public MembershipManagerFactoryImpl(MembershipConfig config,
      ClusterDistributionManager dm) {

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
  public MembershipManagerFactory setMembershipListener(MembershipListener membershipListener) {
    this.membershipListener = membershipListener;
    return this;
  }

  @Override
  public MembershipManagerFactory setMessageListener(MessageListener messageListener) {
    this.messageListener = messageListener;
    return this;
  }

  @Override
  public MembershipManagerFactory setConfig(MembershipConfig config) {
    this.config = config;
    return this;
  }

  @Override
  public InternalMembershipManager create() {
    GMSMembershipManager gmsMembershipManager =
        new GMSMembershipManager(membershipListener, messageListener, dm);
    Services services1 =
        new Services(gmsMembershipManager.getGMSManager(), statistics,
            authenticator,
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
