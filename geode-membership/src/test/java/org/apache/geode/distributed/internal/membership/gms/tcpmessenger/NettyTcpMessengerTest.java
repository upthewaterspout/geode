package org.apache.geode.distributed.internal.membership.gms.tcpmessenger;

import static org.apache.geode.test.awaitility.GeodeAwaitility.await;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.geode.distributed.internal.membership.api.MemberIdentifier;
import org.apache.geode.distributed.internal.membership.api.MemberIdentifierFactoryImpl;
import org.apache.geode.distributed.internal.membership.api.MembershipClosedException;
import org.apache.geode.distributed.internal.membership.api.MembershipConfig;
import org.apache.geode.distributed.internal.membership.gms.Services;
import org.apache.geode.distributed.internal.membership.gms.interfaces.MessageHandler;
import org.apache.geode.distributed.internal.membership.gms.messages.HeartbeatMessage;
import org.apache.geode.internal.serialization.internal.DSFIDSerializerImpl;

public class NettyTcpMessengerTest {

  private DSFIDSerializerImpl serializer;
  private NettyTcpMessenger<MemberIdentifier> messenger1;
  private NettyTcpMessenger<MemberIdentifier> messenger2;

  @Before
  public void setUp() {
    serializer = new DSFIDSerializerImpl();
    Services.registerSerializables(serializer);
    messenger1 = new NettyTcpMessenger<>(serializer, null);
    messenger2 = new NettyTcpMessenger<>(serializer, null);
  }

  @After
  public void tearDown() {
    messenger1.stop();
    messenger2.stop();
  }

  @Test
  public void canSendMessageToRemoteMessenger() {
    MessageHandler handler = mock(MessageHandler.class);
    messenger1.addHandler(HeartbeatMessage.class, handler);
    MembershipConfig membershipConfig = new MembershipConfig() {};
    messenger1.init(membershipConfig, new MemberIdentifierFactoryImpl(), address -> {
    }, () -> null);
    messenger1.start();
    MemberIdentifier memberId1 = messenger1.getMemberID();



    HeartbeatMessage<MemberIdentifier> message = new HeartbeatMessage<>();
    message.setRecipient(memberId1);
    messenger2.send(message);

    await().untilAsserted(() -> {
      verify(handler).processMessage(isA(HeartbeatMessage.class));
    });
  }

  @Test
  public void sendThrowsExceptionWhenMessengerShutdown() {
    MessageHandler handler = mock(MessageHandler.class);
    messenger1.addHandler(HeartbeatMessage.class, handler);
    MembershipConfig membershipConfig = new MembershipConfig() {};
    messenger1.init(membershipConfig, new MemberIdentifierFactoryImpl(), address -> {
    }, () -> null);
    messenger1.start();
    MemberIdentifier memberId1 = messenger1.getMemberID();



    HeartbeatMessage<MemberIdentifier> message = new HeartbeatMessage<>();
    message.setRecipient(memberId1);
    messenger2.stop();
    Assertions.assertThatThrownBy(() -> messenger2.send(message)).isInstanceOf(
        MembershipClosedException.class);
  }

  @Test
  public void stoppingCleansUpResources() throws InterruptedException {
    for(int i =0; i < 8000; i ++ ) {
      System.out.println("Loop " + i);
      messenger1.stop();
      messenger2.stop();

      messenger1 = new NettyTcpMessenger<>(serializer, null);
      messenger2 = new NettyTcpMessenger<>(serializer, null);
      CountDownLatch messageReceived = new CountDownLatch(1);
      MessageHandler handler = m -> messageReceived.countDown();
      messenger1.addHandler(HeartbeatMessage.class, handler);

      MembershipConfig membershipConfig = new MembershipConfig() {};
      messenger1.init(membershipConfig, new MemberIdentifierFactoryImpl(), address -> {
      }, () -> null);
      messenger1.start();
      MemberIdentifier memberId1 = messenger1.getMemberID();

      HeartbeatMessage<MemberIdentifier> message = new HeartbeatMessage<>();
      message.setRecipient(memberId1);
      messenger2.send(message);
      Assertions.assertThat(messageReceived.await(5, TimeUnit.MINUTES)).isTrue();
    }
  }
}
