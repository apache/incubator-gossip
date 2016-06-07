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

import org.apache.gossip.GossipMember;
import org.apache.gossip.GossipSettings;
import org.apache.gossip.LocalGossipMember;
import org.apache.gossip.event.GossipListener;
import org.apache.gossip.event.GossipState;
import org.apache.gossip.manager.random.RandomGossipManager;
import org.junit.Assert;
import org.junit.Test;

import javax.management.Notification;
import javax.management.NotificationListener;
import java.util.ArrayList;
import java.util.List;

public class TestRandomGossipManagerBuilder {

  public static class TestGossipListener implements GossipListener {
    @Override
    public void gossipEvent(GossipMember member, GossipState state) {
      System.out.println("Got gossip event");
    }
  }

  public static class TestNotificationListener implements NotificationListener {
    @Override
    public void handleNotification(Notification notification, Object o) {
      System.out.println("Got notification event");
    }
  }

  @Test(expected=IllegalArgumentException.class)
  public void idShouldNotBeNull() {
    RandomGossipManager.newBuilder().cluster("aCluster").build();
  }

  @Test(expected=IllegalArgumentException.class)
  public void clusterShouldNotBeNull() {
    RandomGossipManager.newBuilder().withId("id").build();
  }

  @Test(expected=IllegalArgumentException.class)
  public void settingsShouldNotBeNull() {
    RandomGossipManager.newBuilder().withId("id").cluster("aCluster").build();
  }

  @Test
  public void createMembersListIfNull() {
    RandomGossipManager gossipManager = RandomGossipManager.newBuilder()
        .withId("id")
        .cluster("aCluster")
        .port(8080)
        .address("localhost")
        .settings(new GossipSettings())
        .gossipMembers(null).build();

    Assert.assertNotNull(gossipManager.getMemberList());
  }

  @Test
  public void useMemberListIfProvided() {
    LocalGossipMember member = new LocalGossipMember("aCluster", "localhost", 2000, "aGossipMember",
        System.currentTimeMillis(), new TestNotificationListener(), 60000);

    List<GossipMember> memberList = new ArrayList<>();
    memberList.add(member);

    RandomGossipManager gossipManager = RandomGossipManager.newBuilder()
        .withId("id")
        .cluster("aCluster")
        .settings(new GossipSettings())
        .gossipMembers(memberList).build();

    Assert.assertEquals(1, gossipManager.getMemberList().size());
    Assert.assertEquals(member.getId(), gossipManager.getMemberList().get(0).getId());
  }

}