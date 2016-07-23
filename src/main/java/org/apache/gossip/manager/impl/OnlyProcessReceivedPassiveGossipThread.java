/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.gossip.manager.impl;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.apache.gossip.GossipMember;
import org.apache.gossip.LocalGossipMember;
import org.apache.gossip.RemoteGossipMember;
import org.apache.gossip.manager.GossipCore;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.manager.PassiveGossipThread;
import org.apache.log4j.Logger;

public class OnlyProcessReceivedPassiveGossipThread extends PassiveGossipThread {
  
  public static final Logger LOGGER = Logger.getLogger(OnlyProcessReceivedPassiveGossipThread.class);

  public OnlyProcessReceivedPassiveGossipThread(GossipManager gossipManager, 
		                                        GossipCore gossipCore,
		                                        CountDownLatch gate) {
    super(gossipManager, gossipCore, gate);
  }

  /**
   * Merge remote list (received from peer), and our local member list. Simply, we must update the
   * heartbeats that the remote list has with our list. Also, some additional logic is needed to
   * make sure we have not timed out a member and then immediately received a list with that member.
   * 
   * @param gossipManager
   * @param senderMember
   * @param remoteList
   */
  protected void mergeLists(GossipManager gossipManager, RemoteGossipMember senderMember,
          List<GossipMember> remoteList) {

    // if the person sending to us is in the dead list consider them up
    for (LocalGossipMember i : gossipManager.getDeadList()) {
      if (i.getId().equals(senderMember.getId())) {
        LOGGER.info(gossipManager.getMyself() + " contacted by dead member " + senderMember.getUri());
        LocalGossipMember newLocalMember = new LocalGossipMember(senderMember.getClusterName(),
                senderMember.getUri(), senderMember.getId(),
                senderMember.getHeartbeat(), gossipManager, gossipManager.getSettings()
                        .getCleanupInterval());
        gossipManager.reviveMember(newLocalMember);
        newLocalMember.startTimeoutTimer();
      }
    }
    for (GossipMember remoteMember : remoteList) {
      if (remoteMember.getId().equals(gossipManager.getMyself().getId())) {
        continue;
      }
      if (gossipManager.getLiveMembers().contains(remoteMember)) {
        LocalGossipMember localMember = gossipManager.getLiveMembers().get(
                gossipManager.getLiveMembers().indexOf(remoteMember));
        if (remoteMember.getHeartbeat() > localMember.getHeartbeat()) {
          localMember.setHeartbeat(remoteMember.getHeartbeat());
          localMember.resetTimeoutTimer();
        }
      } else if (!gossipManager.getLiveMembers().contains(remoteMember)
              && !gossipManager.getDeadList().contains(remoteMember)) {
        LocalGossipMember newLocalMember = new LocalGossipMember(remoteMember.getClusterName(),
                remoteMember.getUri(), remoteMember.getId(),
                remoteMember.getHeartbeat(), gossipManager, gossipManager.getSettings()
                        .getCleanupInterval());
        gossipManager.createOrReviveMember(newLocalMember);
        newLocalMember.startTimeoutTimer();
      } else {
        if (gossipManager.getDeadList().contains(remoteMember)) {
          LocalGossipMember localDeadMember = gossipManager.getDeadList().get(
                  gossipManager.getDeadList().indexOf(remoteMember));
          if (remoteMember.getHeartbeat() > localDeadMember.getHeartbeat()) {
            LocalGossipMember newLocalMember = new LocalGossipMember(remoteMember.getClusterName(),
                    remoteMember.getUri(), remoteMember.getId(),
                    remoteMember.getHeartbeat(), gossipManager, gossipManager.getSettings()
                            .getCleanupInterval());
            gossipManager.reviveMember(newLocalMember);
            newLocalMember.startTimeoutTimer();
            LOGGER.debug("Removed remote member " + remoteMember.getAddress()
                    + " from dead list and added to local member list.");
          } else {
            LOGGER.debug("me " + gossipManager.getMyself());
            LOGGER.debug("sender " + senderMember);
            LOGGER.debug("remote " + remoteList);
            LOGGER.debug("live " + gossipManager.getLiveMembers());
            LOGGER.debug("dead " + gossipManager.getDeadList());
          }
        } else {
          LOGGER.debug("me " + gossipManager.getMyself());
          LOGGER.debug("sender " + senderMember);
          LOGGER.debug("remote " + remoteList);
          LOGGER.debug("live " + gossipManager.getLiveMembers());
          LOGGER.debug("dead " + gossipManager.getDeadList());
          // throw new IllegalArgumentException("wtf");
        }
      }
    }
  }

}
