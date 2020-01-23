package org.apache.geode.distributed.internal.membership.gms;

import static org.apache.geode.distributed.internal.membership.adapter.TcpSocketCreatorAdapter.asTcpSocketCreator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.distributed.internal.membership.api.LifecycleListener;
import org.apache.geode.distributed.internal.membership.api.MemberData;
import org.apache.geode.distributed.internal.membership.api.MemberIdentifier;
import org.apache.geode.distributed.internal.membership.api.MemberIdentifierFactory;
import org.apache.geode.distributed.internal.membership.api.MemberStartupException;
import org.apache.geode.distributed.internal.membership.api.Membership;
import org.apache.geode.distributed.internal.membership.api.MembershipBuilder;
import org.apache.geode.distributed.internal.membership.api.MembershipConfig;
import org.apache.geode.distributed.internal.membership.api.MembershipConfigurationException;
import org.apache.geode.distributed.internal.membership.api.MembershipLocator;
import org.apache.geode.distributed.internal.membership.api.MembershipLocatorBuilder;
import org.apache.geode.distributed.internal.tcpserver.TcpClient;
import org.apache.geode.distributed.internal.tcpserver.TcpSocketCreator;
import org.apache.geode.internal.InternalDataSerializer;
import org.apache.geode.internal.admin.SSLConfig;
import org.apache.geode.internal.net.SocketCreator;
import org.apache.geode.internal.serialization.DSFIDSerializer;
import org.apache.geode.logging.internal.executors.LoggingExecutors;

public class MembershipIntegrationTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private InetAddress localHost;
  private DSFIDSerializer dsfidSerializer;
  private TcpSocketCreator socketCreator;

  @Before
  public void before() throws IOException, MembershipConfigurationException {
    localHost = InetAddress.getLocalHost();

    // TODO - using geode-core serializer. This is needed to have be able to
    // read InternalDistributedMember.
    dsfidSerializer = InternalDataSerializer.getDSFIDSerializer();

    // TODO - using geode-core socket creator
    socketCreator = asTcpSocketCreator(new SocketCreator(new SSLConfig.Builder().build()));
  }

  @Test
  public void oneMembershipCanStartWithALocator()
      throws IOException, MemberStartupException {
    final MembershipLocator<InternalDistributedMember> locator = createLocator();
    locator.start();

    final Membership<InternalDistributedMember> membership = createMembership(locator,
        locator.getPort());
    start(membership);

    assertThat(membership.getView().getMembers()).hasSize(1);
  }

  @Test
  public void twoMembersCanStartWithOneLocator()
      throws IOException, MemberStartupException {
    MembershipLocator<InternalDistributedMember> locator = createLocator();
    locator.start();
    int locatorPort = locator.getPort();

    Membership<InternalDistributedMember> membership1 = createMembership(locator, locatorPort);
    start(membership1);

    Membership<InternalDistributedMember> membership2 = createMembership(null, locatorPort);
    start(membership2);

    assertThat(membership1.getView().getMembers()).hasSize(2);
    assertThat(membership2.getView().getMembers()).hasSize(2);
  }

  @Test
  public void twoLocatorsCanStartSequentially()
      throws IOException, MemberStartupException {

    MembershipLocator<InternalDistributedMember> locator1 = createLocator();
    locator1.start();
    int locatorPort1 = locator1.getPort();

    Membership<InternalDistributedMember> membership1 = createMembership(locator1, locatorPort1);
    start(membership1);

    MembershipLocator<InternalDistributedMember> locator2 = createLocator(locatorPort1);
    locator2.start();
    int locatorPort2 = locator2.getPort();

    Membership<InternalDistributedMember> membership2 =
        createMembership(locator2, locatorPort1, locatorPort2);
    start(membership2);

    assertThat(membership1.getView().getMembers()).hasSize(2);
    assertThat(membership2.getView().getMembers()).hasSize(2);
  }

  @Test
  public void secondMembershipCanJoinUsingATheSecondLocatorToStart()
      throws IOException, MemberStartupException {

    MembershipLocator<InternalDistributedMember> locator1 = createLocator();
    locator1.start();
    int locatorPort1 = locator1.getPort();

    Membership<InternalDistributedMember> membership1 = createMembership(locator1, locatorPort1);
    start(membership1);

    MembershipLocator<InternalDistributedMember> locator2 = createLocator(locatorPort1);
    locator2.start();
    int locatorPort2 = locator2.getPort();

    // Force the next membership to use locator2 by stopping locator1
    locator1.stop();

    Membership<InternalDistributedMember> membership2 =
        createMembership(locator2, locatorPort1, locatorPort2);
    start(membership2);

    assertThat(membership1.getView().getMembers()).hasSize(2);
    assertThat(membership2.getView().getMembers()).hasSize(2);
  }

  @Test
  public void twoLocatorsCanStartConcurrently() {

  }

  @Test
  public void oneMembershipFailsToStartWithoutRunningLocator() {

  }

  private void start(Membership<InternalDistributedMember> membership)
      throws MemberStartupException {
    membership.start();
    membership.startEventProcessing();
  }

  private Membership<InternalDistributedMember> createMembership(
      MembershipLocator<InternalDistributedMember> embeddedLocator, int... locatorPorts)
      throws MembershipConfigurationException {
    final boolean isALocator = embeddedLocator != null;
    MembershipConfig config = createMembershipConfig(isALocator, locatorPorts);

    // TODO - using geode-core InternalDistributedMember
    MemberIdentifierFactory<InternalDistributedMember> memberIdFactory =
        new MemberIdentifierFactory<InternalDistributedMember>() {
          @Override
          public InternalDistributedMember create(MemberData memberInfo) {
            return new InternalDistributedMember(memberInfo);
          }

          @Override
          public Comparator<InternalDistributedMember> getComparator() {
            return InternalDistributedMember::compareTo;
          }
        };

    TcpClient locatorClient = new TcpClient(socketCreator, dsfidSerializer.getObjectSerializer(),
        dsfidSerializer.getObjectDeserializer());

    LifecycleListener<InternalDistributedMember> lifeCycleListener = mock(LifecycleListener.class);

    final Membership<InternalDistributedMember> membership =
        MembershipBuilder.<InternalDistributedMember>newMembershipBuilder(
            socketCreator, locatorClient, dsfidSerializer, memberIdFactory)
            .setConfig(config)
            .setLifecycleListener(lifeCycleListener)
            .create();

    if (isALocator) {
      // TODO - the membership *must* be installed in the locator at this special
      // point during membership startup for the start to succeed
      doAnswer(invocation -> {
        embeddedLocator.setMembership(membership);
        return null;
      }).when(lifeCycleListener).started();
    }
    return membership;
  }

  private MembershipConfig createMembershipConfig(boolean isALocator, int[] locatorPorts) {
    return new MembershipConfig() {
      public String getLocators() {
        return getLocatorString(locatorPorts);
      }

      // TODO - the Membership system starting in the locator *MUST* be told that is
      // is a locator through this flag. Ideally it should be able to infer this from
      // being associated with a locator
      @Override
      public int getVmKind() {
        return isALocator ? MemberIdentifier.LOCATOR_DM_TYPE : MemberIdentifier.NORMAL_DM_TYPE;
      }
    };
  }

  private String getLocatorString(int... locatorPorts) {
    String hostName = localHost.getHostName();
    return Arrays.stream(locatorPorts)
        .mapToObj(port -> hostName + '[' + port + ']')
        .collect(Collectors.joining(","));
  }

  private MembershipLocator<InternalDistributedMember> createLocator(int... locatorPorts)
      throws MembershipConfigurationException,
      IOException {
    final Supplier<ExecutorService> executorServiceSupplier =
        () -> LoggingExecutors.newCachedThreadPool("membership", false);
    Path locatorDirectory = temporaryFolder.newFolder().toPath();

    MembershipConfig config = createMembershipConfig(true, locatorPorts);

    MembershipLocator<InternalDistributedMember> membershipLocator =
        MembershipLocatorBuilder.<InternalDistributedMember>newLocatorBuilder(
            socketCreator,
            dsfidSerializer.getObjectSerializer(),
            dsfidSerializer.getObjectDeserializer(),
            locatorDirectory,
            executorServiceSupplier)
            .setConfig(config)
            .create();

    return membershipLocator;
  }


}
