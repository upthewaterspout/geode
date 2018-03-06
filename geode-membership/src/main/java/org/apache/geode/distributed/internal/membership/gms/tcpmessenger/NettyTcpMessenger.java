package org.apache.geode.distributed.internal.membership.gms.tcpmessenger;

import static org.apache.geode.distributed.internal.membership.api.Message.ALL_RECIPIENTS;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import org.apache.geode.distributed.internal.membership.api.MemberIdentifier;
import org.apache.geode.distributed.internal.membership.api.MemberIdentifierFactory;
import org.apache.geode.distributed.internal.membership.api.MembershipConfig;
import org.apache.geode.distributed.internal.membership.api.Message;
import org.apache.geode.distributed.internal.membership.api.QuorumChecker;
import org.apache.geode.distributed.internal.membership.gms.GMSMemberData;
import org.apache.geode.distributed.internal.membership.gms.GMSMembershipView;
import org.apache.geode.distributed.internal.membership.gms.GMSUtil;
import org.apache.geode.distributed.internal.membership.gms.Services;
import org.apache.geode.distributed.internal.membership.gms.interfaces.MessageHandler;
import org.apache.geode.distributed.internal.membership.gms.interfaces.Messenger;
import org.apache.geode.distributed.internal.membership.gms.messenger.GMSQuorumChecker;
import org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty.client.NettyClientManager;
import org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty.server.NettyServer;
import org.apache.geode.internal.inet.LocalHostUtil;
import org.apache.geode.internal.serialization.DSFIDSerializer;
import org.apache.geode.internal.serialization.KnownVersion;
import org.apache.geode.logging.internal.OSProcess;

public class NettyTcpMessenger<ID extends MemberIdentifier> implements Messenger<ID> {
  public static final List<MemberIdentifier> ALL_MEMBERS =
      Collections.singletonList(ALL_RECIPIENTS);
  private final NettyServer server;
  private final NettyClientManager client;
  private ID localAddress;
  private GMSMembershipView<ID> currentView;
  private MembershipConfig config;
  private MemberIdentifierFactory<ID> memberFactory;
  private Consumer<ID> localAddressListener;
  private Supplier<GMSMembershipView<ID>> currentViewSupplier;

  public NettyTcpMessenger(DSFIDSerializer serializer,
      MessageHandler<ID> defaultHandler) {
    client = new NettyClientManager(serializer);
    server = new NettyServer(serializer, defaultHandler);
  }

  @Override
  public <T extends Message<ID>> void addHandler(Class<T> c, MessageHandler<T> h) {
    server.addHandler(c, h);
  }

  @Override
  public Set<ID> send(Message<ID> m, GMSMembershipView<ID> alternateView) {
    return send(m);
  }

  @Override
  public Set<ID> send(Message<ID> m) {
    List<ID> recipients = m.getRecipients();
    if (recipients.equals(ALL_MEMBERS)) {
      recipients = currentViewSupplier.get().getMembers();
    }

    for (ID member : recipients) {
      String host = member.getHost();
      int membershipPort = member.getMembershipPort();
      client.send(new InetSocketAddress(host, membershipPort), m, localAddress);
    }

    return Collections.emptySet();
  }

  @Override
  public Set<ID> sendUnreliably(Message<ID> m) {
    return send(m);
  }

  @Override
  public ID getMemberID() {
    return localAddress;
  }

  @Override
  public void init(Services<ID> services) {
    init(services.getConfig(), services.getMemberFactory(), services::setLocalAddress, () -> services.getJoinLeave().getView());
  }

  void init(MembershipConfig config, MemberIdentifierFactory<ID> memberFactory,
      Consumer<ID> localAddressListener, Supplier<GMSMembershipView<ID>> currentViewSupplier) {
    this.config = config;
    this.memberFactory = memberFactory;
    this.localAddressListener = localAddressListener;
    this.currentViewSupplier = currentViewSupplier;
  }

  @Override
  public void start() {
    String bindAddress = config.getBindAddress();
    InetSocketAddress serverAddress;
    try {
      serverAddress = StringUtils.isEmpty(bindAddress) ? new InetSocketAddress(0)
          : InetSocketAddress.createUnresolved(bindAddress, 0);
      server.start(serverAddress);
      serverAddress = server.getAddress();
      if (serverAddress.getAddress().isAnyLocalAddress()) {
        serverAddress =
            new InetSocketAddress(LocalHostUtil.getLocalHost(), serverAddress.getPort());
      }
    } catch (InterruptedException | UnknownHostException e) {
      throw new IllegalStateException(e);
    }


    // This junk all came from JGroupsMessenger. Refactor into a common class?
    boolean isLocator = (config
        .getVmKind() == MemberIdentifier.LOCATOR_DM_TYPE)
        || !config.getStartLocator().isEmpty();

    // establish the DistributedSystem's address
    String hostname =
        !config.isNetworkPartitionDetectionEnabled() ? serverAddress.getHostName()
            : serverAddress.getHostString();
    GMSMemberData gmsMember = new GMSMemberData(serverAddress.getAddress(),
        hostname, serverAddress.getPort(),
        OSProcess.getId(), (byte) config.getVmKind(),
        -1 /* directport */, -1 /* viewID */, config.getName(),
        GMSUtil.parseGroups(config.getRoles(), config.getGroups()), config.getDurableClientId(),
        config.getDurableClientTimeout(),
        config.isNetworkPartitionDetectionEnabled(), isLocator,
        KnownVersion.getCurrentVersion().ordinal(),
        0, 0,
        (byte) (config.getMemberWeight() & 0xff), false, null);
    localAddress = memberFactory.create(gmsMember);
    localAddressListener.accept(localAddress);
  }


  @Override
  public void stop() {
    //TODO - the jgroups messenger has this code to leave the channel open if it is shutdown?
//    if ((services.isShutdownDueToForcedDisconnect() && services.isAutoReconnectEnabled())
//        || services.getManager().isReconnectingDS()) {
    client.close();
    server.close();
  }

  @Override
  public void emergencyClose() {
    this.stop();
  }


  @Override
  public void installView(GMSMembershipView<ID> v) {
    Set<ID> crashedMembers = v.getActualCrashedMembers(currentView);
    this.currentView = v;
    for (ID member : crashedMembers) {
      client.shutdown(new InetSocketAddress(member.getHost(), member.getMembershipPort()));
    }

  }

  //------------------------------------------------------------
  // Implementation still needed!
  //------------------------------------------------------------

  @Override
  public QuorumChecker getQuorumChecker() {
    throw new IllegalStateException("Quorum checking not supported");
  }


  //------------------------------------------------------------
  //Methods we don't need to implement for this messenger
  //------------------------------------------------------------

  @Override
  public void getMessageState(ID member, Map<String, Long> state,
                              boolean includeMulticast) {
    //Nothing needed here. The old jgroups messenger only implemented this
    //when using multicast mode, which this meesenger does not support.
  }

  @Override
  public void waitForMessageState(ID member, Map<String, Long> state)
      throws InterruptedException {
    //Nothing needed here. The old jgroups messenger only implemented this
    //when using multicast mode, which this meesenger does not support.
  }

  @Override
  public void started() {

  }

  @Override
  public void stopped() {

  }

  @Override
  public void beSick() {

  }

  @Override
  public void playDead() {

  }

  @Override
  public void beHealthy() {

  }

  @Override
  public void memberSuspected(ID initiator,
      ID suspect, String reason) {

  }

  @Override
  public byte[] getPublicKey(ID mbr) {
    //This is what the jgroups messenger returns if UDP encryption is turned off
    return null;
  }

  @Override
  public byte[] getClusterSecretKey() {
    throw null;
  }

  @Override
  public void initClusterKey() {
  }

  @Override
  public void setPublicKey(byte[] publickey, ID mbr) {
  }

  @Override
  public void setClusterSecretKey(byte[] clusterSecretKey) {
  }

  //------------------------------------------------------------
  //Unsupported UDP related features
  //------------------------------------------------------------

  @Override
  public boolean testMulticast(long timeout) throws InterruptedException {
    throw new UnsupportedOperationException();
  }


}
