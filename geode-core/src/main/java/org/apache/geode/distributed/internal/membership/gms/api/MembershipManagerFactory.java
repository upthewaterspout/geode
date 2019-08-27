/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.distributed.internal.membership.gms.api;


import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.distributed.internal.membership.DistributedMembershipListener;
import org.apache.geode.distributed.internal.membership.InternalMembershipManager;
import org.apache.geode.distributed.internal.membership.NetMember;
import org.apache.geode.distributed.internal.membership.gms.MembershipManagerFactoryImpl;
import org.apache.geode.internal.admin.remote.RemoteTransportConfig;

/**
 * Create a new Member based on the given inputs. TODO: need to implement a real factory
 * implementation based on gemfire.properties
 *
 * @see NetMember
 */
public interface MembershipManagerFactory {

  MembershipManagerFactory setAuthenticator(Authenticator authenticator);

  MembershipManagerFactory setStatistics(MembershipStatistics statistics);

  InternalMembershipManager create();

  /**
   * Create a new MembershipManager. Be sure to send the manager a postConnect() message before you
   * start using it.
   *
   * @param listener the listener to notify for callbacks
   * @param transport holds configuration information that can be used by the manager to configure
   *        itself
   * @param stats are used for recording statistical communications information
   * @return a MembershipManager
   */
  public static InternalMembershipManager newMembershipManager(
      final DistributedMembershipListener listener,
      final RemoteTransportConfig transport,
      final MembershipStatistics stats,
      final Authenticator authenticator,
      final DistributionConfig config) {
    return newMembershipManagerFactory(listener, transport, config)
        .setAuthenticator(authenticator)
        .setStatistics(stats)
        .create();
  }

  static MembershipManagerFactory newMembershipManagerFactory(
      DistributedMembershipListener listener, RemoteTransportConfig transport,
      DistributionConfig config) {

    return new MembershipManagerFactoryImpl(listener, transport,
        config);
  }

}
