package org.apache.geode.distributed.internal.membership.gms.api;

public interface MembershipStatistics {
  long startMsgSerialization();

  void endMsgSerialization(long start);

  long startUDPMsgEncryption();

  void endUDPMsgEncryption(long start);

  long startUDPMsgDecryption();

  void endUDPMsgDecryption(long start);

  long startMsgDeserialization();

  void endMsgDeserialization(long start);

  /**
   * Increments the total number of message bytes sent by the distribution manager
   */
  void incSentBytes(long bytes);

  long startUDPDispatchRequest();

  void endUDPDispatchRequest(long start);

  /**
   * increments the number of unicast writes performed and the number of bytes written
   *
   * @since GemFire 5.0
   */
  void incUcastWriteBytes(int bytesWritten);

  /**
   * increment the number of unicast datagram payload bytes received and the number of unicast reads
   * performed
   */
  void incUcastReadBytes(int amount);

  /**
   * increment the number of multicast datagrams sent and the number of multicast bytes transmitted
   */
  void incMcastWriteBytes(int bytesWritten);

  /**
   * increment the number of multicast datagram payload bytes received, and the number of mcast
   * messages read
   */
  void incMcastReadBytes(int amount);

  /**
   * increment the number of unicast UDP retransmission requests received from other processes
   *
   * @since GemFire 5.0
   */
  void incUcastRetransmits();

  /**
   * increment the number of multicast UDP retransmissions sent to other processes
   *
   * @since GemFire 5.0
   */
  void incMcastRetransmits();

  /**
   * increment the number of multicast UDP retransmission requests sent to other processes
   *
   * @since GemFire 5.0
   */
  void incMcastRetransmitRequests();

  void incHeartbeatRequestsSent();

  void incHeartbeatRequestsReceived();

  void incHeartbeatsSent();

  void incHeartbeatsReceived();

  void incSuspectsSent();

  void incSuspectsReceived();

  void incFinalCheckRequestsSent();

  void incFinalCheckRequestsReceived();

  void incFinalCheckResponsesSent();

  void incFinalCheckResponsesReceived();

  void incTcpFinalCheckRequestsSent();

  void incTcpFinalCheckRequestsReceived();

  void incTcpFinalCheckResponsesSent();

  void incTcpFinalCheckResponsesReceived();

  void incUdpFinalCheckRequestsSent();

  void incUdpFinalCheckResponsesReceived();

  long getHeartbeatRequestsReceived();

  long getHeartbeatsSent();

  long getSuspectsSent();

  long getSuspectsReceived();

  long getFinalCheckRequestsSent();

  long getFinalCheckRequestsReceived();

  long getFinalCheckResponsesSent();

  long getFinalCheckResponsesReceived();

  long getTcpFinalCheckRequestsSent();

  long getTcpFinalCheckRequestsReceived();

  long getTcpFinalCheckResponsesSent();

  long getTcpFinalCheckResponsesReceived();

  // Stats for GMSHealthMonitor
  long getHeartbeatRequestsSent();
}
