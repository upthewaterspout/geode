package org.apache.geode.distributed.internal.membership.gms;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import org.apache.geode.distributed.internal.membership.api.MemberIdentifier;
import org.apache.geode.distributed.internal.membership.api.MemberIdentifierFactoryImpl;
import org.apache.geode.distributed.internal.membership.api.MemberStartupException;
import org.apache.geode.distributed.internal.membership.api.Membership;
import org.apache.geode.distributed.internal.membership.api.MembershipBuilder;
import org.apache.geode.distributed.internal.membership.api.MembershipConfig;
import org.apache.geode.distributed.internal.membership.api.MembershipConfigurationException;
import org.apache.geode.distributed.internal.membership.api.MembershipLocator;
import org.apache.geode.distributed.internal.membership.api.MembershipLocatorBuilder;
import org.apache.geode.distributed.internal.membership.gms.scheduler.test.TestSchedulerParallel;
import org.apache.geode.distributed.internal.tcpserver.TcpClient;
import org.apache.geode.distributed.internal.tcpserver.TcpSocketCreator;
import org.apache.geode.distributed.internal.tcpserver.TcpSocketCreatorImpl;
import org.apache.geode.internal.serialization.DSFIDSerializer;
import org.apache.geode.internal.serialization.internal.DSFIDSerializerImpl;
import org.apache.geode.logging.internal.executors.LoggingExecutors;

public class MembershipIntegrationTest {
  private InetAddress localHost;
  private DSFIDSerializer dsfidSerializer;
  private TcpSocketCreator socketCreator;
  // when using TestScheduler
  // private VirtualTime time;
  private TestSchedulerParallel scheduler;

  @Before
  public void before() throws IOException, MembershipConfigurationException {
    localHost = InetAddress.getLocalHost();
    dsfidSerializer = new DSFIDSerializerImpl();
    socketCreator = new TcpSocketCreatorImpl();

    // time = new VirtualTime();
    scheduler = new TestSchedulerParallel();
  }

  @Test
  public void oneMembershipCanStartWithALocator()
      throws IOException, MemberStartupException {
    final MembershipLocator<MemberIdentifier> locator = createLocator(0);
    locator.start();

    final Membership<MemberIdentifier> membership = createMembership(locator,
        locator.getPort());
    start(membership);

    assertThat(membership.getView().getMembers()).hasSize(1);
  }

  @Test
  public void twoMembersCanStartWithOneLocator()
      throws IOException, MemberStartupException {
    final MembershipLocator<MemberIdentifier> locator = createLocator(0);
    locator.start();
    final int locatorPort = locator.getPort();

    final Membership<MemberIdentifier> membership1 = createMembership(locator, locatorPort);
    start(membership1);

    final Membership<MemberIdentifier> membership2 = createMembership(null, locatorPort);
    start(membership2);

    assertThat(membership1.getView().getMembers()).hasSize(2);
    assertThat(membership2.getView().getMembers()).hasSize(2);
  }

  @Test
  public void twoLocatorsCanStartSequentially()
      throws IOException, MemberStartupException {

    final MembershipLocator<MemberIdentifier> locator1 = createLocator(0);
    locator1.start();
    final int locatorPort1 = locator1.getPort();

    Membership<MemberIdentifier> membership1 = createMembership(locator1, locatorPort1);
    start(membership1);

    final MembershipLocator<MemberIdentifier> locator2 = createLocator(0, locatorPort1);
    locator2.start();
    final int locatorPort2 = locator2.getPort();

    Membership<MemberIdentifier> membership2 =
        createMembership(locator2, locatorPort1, locatorPort2);
    start(membership2);

    assertThat(membership1.getView().getMembers()).hasSize(2);
    assertThat(membership2.getView().getMembers()).hasSize(2);
  }

  @Test
  public void secondMembershipCanJoinUsingATheSecondLocatorToStart()
      throws IOException, MemberStartupException {

    final MembershipLocator<MemberIdentifier> locator1 = createLocator(0);
    locator1.start();
    final int locatorPort1 = locator1.getPort();

    final Membership<MemberIdentifier> membership1 = createMembership(locator1, locatorPort1);
    start(membership1);

    final MembershipLocator<MemberIdentifier> locator2 = createLocator(0, locatorPort1);
    locator2.start();
    int locatorPort2 = locator2.getPort();

    // Force the next membership to use locator2 by stopping locator1
    locator1.stop();

    Membership<MemberIdentifier> membership2 =
        createMembership(locator2, locatorPort1, locatorPort2);
    start(membership2);

    assertThat(membership1.getView().getMembers()).hasSize(2);
    assertThat(membership2.getView().getMembers()).hasSize(2);
  }

  @Test
  public void twoLocatorsCanStartConcurrently() {
    // TODO: maybe reinstate this after AvailablePortHelper breaks dependency on DistributionConfig
    // final int[] ports = AvailablePortHelper.getRandomAvailableTCPPorts(2);
    final int[] ports = new int[] {8010, 8011};

    final AtomicReference<Membership<MemberIdentifier>> membership1 = new AtomicReference<>();
    scheduler.schedule(() -> {
      MembershipLocator<MemberIdentifier> locator1 = createLocator(ports[0], ports);
      locator1.start();

      final Membership<MemberIdentifier> membership = createMembership(locator1, ports);
      start(membership);
      membership1.set(membership);
    });

    final AtomicReference<Membership<MemberIdentifier>> membership2 = new AtomicReference<>();
    scheduler.schedule(() -> {
      MembershipLocator<MemberIdentifier> locator2 = createLocator(ports[1], ports);
      locator2.start();

      Membership<MemberIdentifier> membership = createMembership(locator2, ports);
      start(membership);
      membership2.set(membership);
    });

    // when using TestScheduler
    // scheduler.triggerActions();

    // TODO - these assertions need an awaitility, because these members may have not received
    // the updated view yet
    assertThat(membership1.get().getView().getMembers()).hasSize(2);
    assertThat(membership2.get().getView().getMembers()).hasSize(2);
  }

  @Test
  public void oneMembershipFailsToStartWithoutRunningLocator() {

  }

  private void start(final Membership<MemberIdentifier> membership)
      throws MemberStartupException {
    membership.start();
    membership.startEventProcessing();
  }

  private Membership<MemberIdentifier> createMembership(
      final MembershipLocator<MemberIdentifier> embeddedLocator,
      final int... locatorPorts)
      throws MembershipConfigurationException {
    final boolean isALocator = embeddedLocator != null;
    final MembershipConfig config = createMembershipConfig(isALocator, locatorPorts);

    final MemberIdentifierFactoryImpl memberIdFactory = new MemberIdentifierFactoryImpl();

    final TcpClient locatorClient =
        new TcpClient(socketCreator, dsfidSerializer.getObjectSerializer(),
            dsfidSerializer.getObjectDeserializer());

    return MembershipBuilder.<MemberIdentifier>newMembershipBuilder(
        socketCreator, locatorClient, dsfidSerializer, memberIdFactory)
        .setMembershipLocator(embeddedLocator)
        .setConfig(config)
        .create();
  }

  private MembershipConfig createMembershipConfig(
      final boolean isALocator,
      final int[] locatorPorts) {
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

  private String getLocatorString(
      final int... locatorPorts) {
    final String hostName = localHost.getHostName();
    return Arrays.stream(locatorPorts)
        .mapToObj(port -> hostName + '[' + port + ']')
        .collect(Collectors.joining(","));
  }

  private MembershipLocator<MemberIdentifier> createLocator(
      final int localPort,
      final int... locatorPorts)
      throws MembershipConfigurationException,
      IOException {
    final Supplier<ExecutorService> executorServiceSupplier =
        () -> LoggingExecutors.newCachedThreadPool("membership", false);
    // Path locatorDirectory = temporaryFolder.newFolder().toPath();

    // TODO - total hack!
    final File locatorFile = File.createTempFile("locator", "");
    locatorFile.mkdirs();
    FileUtils.forceDeleteOnExit(locatorFile);
    final Path locatorDirectory = locatorFile.toPath();

    final MembershipConfig config = createMembershipConfig(true, locatorPorts);

    return MembershipLocatorBuilder.<MemberIdentifier>newLocatorBuilder(
        socketCreator,
        dsfidSerializer,
        locatorDirectory,
        executorServiceSupplier)
        .setConfig(config)
        .setPort(localPort)
        .create();
  }

}
