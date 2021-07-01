package org.apache.geode.distributed.internal.membership.gms.messenger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.jcajce.util.DefaultJcaJceHelper;
import org.bouncycastle.tls.DTLSClientProtocol;
import org.bouncycastle.tls.DTLSServerProtocol;
import org.bouncycastle.tls.DTLSTransport;
import org.bouncycastle.tls.DatagramTransport;
import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.DefaultTlsServer;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsClient;
import org.bouncycastle.tls.TlsServer;
import org.bouncycastle.tls.UDPTransport;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCrypto;
import org.jgroups.Address;
import org.jgroups.stack.IpAddress;

import org.apache.geode.distributed.internal.membership.api.MemberIdentifier;
import org.apache.geode.logging.internal.executors.LoggingExecutors;

public class DTLSJGroupsTransport<ID extends MemberIdentifier> extends Transport<ID> {
  private final Map<Address, DTLSTransport> clientSessions = new HashMap<>();
  private final Map<Address, DTLSServerSession> serverSessions = new HashMap<>();
  private final ExecutorService sendingService =
      LoggingExecutors.newSingleThreadExecutor("dtls sending thread", true);
  private final ExecutorService receivingService =
      LoggingExecutors.newCachedThreadPool("dtls receiving thread thread", true);

  private final SecureRandom random = new SecureRandom();
  private final TlsCrypto crypto = new JcaTlsCrypto(new DefaultJcaJceHelper(), random, random) {};

  @Override
  public void destroy() {
    sendingService.shutdown();
    receivingService.shutdown();
    // TODO - clean up client and server sessions
    super.destroy();
  }

  @Override
  public void receive(Address sender, byte[] data, int offset, int length) {
    super.receive(sender, data, offset, length);
    DTLSServerSession session = serverSessions.computeIfAbsent(sender, this::createServerSession);
    session.addData(data, offset, length);
  }

  private DTLSServerSession createServerSession(Address address) {
    // Create a DTLS server session with our own transport class that gets data from a queue
    // Create a thread that is receiving bytes from the session and calling super.receive if it
    // gets some
    TlsServer server = new DefaultTlsServer(crypto) {
      // Override e.g. TlsServer.getRSASignerCredentials() or
      // similar here, depending on what credentials you wish to use.
    };

    DTLSServerSession transport = new DTLSServerSession((IpAddress) address);
    receivingService.submit(() -> {

      DTLSServerProtocol protocol = new DTLSServerProtocol();

      // SO this method is doing a receive plus performing the TLS handshake I guess?
      // Not sure how to match that to a jgroups protocol.
      // This is smelling like this needs to go in the transport class.
      DTLSTransport dtls = protocol.accept(server, transport);

      while (true) {
        // TODO - what buffer size, timeout, etc. to use?
        // TODO - how does this thing get shutdown? How do we tell that the sending
        // I believe a 0 timeout means wait forever...
        // side has closed the session or gone away...?
        byte[] buf = new byte[65536];
        int receivedBytes = dtls.receive(buf, 0, buf.length, 0);

        super.receive(address, buf, 0, receivedBytes);
      }
    });
  }

  private DTLSTransport getOrCreateClient(Address address) {
    return clientSessions.computeIfAbsent(address, this::createClient);
  }

  @Override
  protected void _send(InetAddress dest, int port, boolean mcast, byte[] data, int offset,
      int length) throws Exception {
    // Create a DTLS client if necessary.
    // Pass bytes that client ... this will handshake synchronously, which isn't great. Perhaps
    // use a queue to pass the bytes to the client to send?
    sendingService.submit(() -> {
      final DTLSTransport transport = getOrCreateClient(dest);
      transport.send(data, offset, length);
    });
    super._send(dest, port, mcast, data, offset, length);
  }


  private DTLSTransport createClient(Address dest) throws IOException {
    // InetAddress address = InetAddress.getByName("www.example.com"); int port = 443;
    // Socket s = new Socket(address, port);
    TlsClient client = new DefaultTlsClient(crypto) {
      @Override
      public TlsAuthentication getAuthentication() throws IOException {
        // TODO - for client authentication, we should return our credentials here
        return null;
      }
    };

    DatagramSocket socket = new DatagramSocket();
    final IpAddress destAddress = (IpAddress) dest;
    socket.connect(destAddress.getIpAddress(), destAddress.getPort());
    // TODO - what MTU to use? jgroups fragments the message at 60K, but then adds headers
    // And perhaps DTLS increases the size as well? anything over 64K will probably choke.
    DatagramTransport transport = new UDPTransport(this.mcast_sock, 65000);
    DTLSClientProtocol protocol = new DTLSClientProtocol();
    DTLSTransport dtls = protocol.connect(client, transport);

    return dtls;
  }


  private class DTLSServerSession implements DatagramTransport {

    private final SynchronousQueue<ByteBuffer> queue = new SynchronousQueue<>();
    private final IpAddress address;

    private DTLSServerSession(IpAddress address) {
      this.address = address;
    }


    public void addData(byte[] data, int offset, int length) {
      queue.offer(ByteBuffer.wrap(data, offset, length));

    }

    @Override
    public int getReceiveLimit() throws IOException {
      // TODO - what to set this too? See UDPTransport...
      return 65000;
    }

    @Override
    public int receive(byte[] buf, int off, int len, int waitMillis) throws IOException {
      ByteBuffer received = queue.poll(waitMillis, TimeUnit.MILLISECONDS);
      final int receivedBytes = received.remaining();
      if (len < receivedBytes) {
        throw new IllegalStateException("Trying to receive a message with too small of a buffer");
      }
      received.get(buf, off, receivedBytes);

      return receivedBytes;
    }

    @Override
    public int getSendLimit() throws IOException {
      return 63000;
    }

    @Override
    public void send(byte[] buf, int off, int len) throws IOException {
      DatagramPacket packet =
          new DatagramPacket(buf, off, len, address.getIpAddress(), address.getPort());
      if (sock != null) {
        sock.send(packet);
      }

    }

    @Override
    public void close() throws IOException {

    }
  }

}
