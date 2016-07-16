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
package org.apache.gossip.manager;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.gossip.GossipService;
import org.apache.gossip.LocalGossipMember;

/**
 * [The active thread: periodically send gossip request.] The class handles gossiping the membership
 * list. This information is important to maintaining a common state among all the nodes, and is
 * important for detecting failures.
 */
abstract public class ActiveGossipThread implements Callable<Boolean> {

  protected final GossipManager gossipManager;

  private final AtomicBoolean keepRunning;

  public ActiveGossipThread(GossipManager gossipManager) {
    this.gossipManager = gossipManager;
    this.keepRunning = new AtomicBoolean(true);
  }

  @Override
  public Boolean call() throws Exception {
    while (keepRunning.get()) {
      try {
        TimeUnit.MILLISECONDS.sleep(gossipManager.getSettings().getGossipInterval());
        sendMembershipList(gossipManager.getMyself(), gossipManager.getMemberList());
      } catch (InterruptedException e) {
        GossipService.LOGGER.error(e);
        keepRunning.set(false);
      }
    }
    shutdown();
    return keepRunning.get();
  }

  public void shutdown() {
    keepRunning.set(false);
  }

  /**
   * Performs the sending of the membership list, after we have incremented our own heartbeat.
   */
  abstract protected void sendMembershipList(LocalGossipMember me,
          List<LocalGossipMember> memberList);

  /**
   * Abstract method which should be implemented by a subclass. This method should return a member
   * of the list to gossip with.
   * 
   * @param memberList
   *          The list of members which are stored in the local list of members.
   * @return The chosen LocalGossipMember to gossip with.
   */
  abstract protected LocalGossipMember selectPartner(List<LocalGossipMember> memberList);
}
