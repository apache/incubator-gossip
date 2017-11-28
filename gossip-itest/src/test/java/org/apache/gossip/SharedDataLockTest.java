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
package org.apache.gossip;

import org.apache.gossip.lock.exceptions.VoteFailedException;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.manager.GossipManagerBuilder;
import org.apache.gossip.model.SharedDataMessage;
import org.apache.gossip.utils.TimeUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnitPlatform.class)
public class SharedDataLockTest extends AbstractIntegrationBase {

  @Test
  public void sharedDataLockRandomVoteTest()
          throws InterruptedException, UnknownHostException, URISyntaxException {
    GossipSettings settings = new GossipSettings();
    settings.setPersistRingState(false);
    settings.setPersistDataState(false);
    String cluster = UUID.randomUUID().toString();
    int seedNodes = 1;
    List<Member> startupMembers = new ArrayList<>();
    for (int i = 1; i < seedNodes + 1; ++i) {
      URI uri = new URI("udp://" + "127.0.0.1" + ":" + (50000 + i));
      startupMembers.add(new RemoteMember(cluster, uri, i + ""));
    }
    final List<GossipManager> clients = new ArrayList<>();
    final int clusterMembers = 10;
    for (int i = 1; i < clusterMembers + 1; ++i) {
      URI uri = new URI("udp://" + "127.0.0.1" + ":" + (50000 + i));
      GossipManager gossipService = GossipManagerBuilder.newBuilder().cluster(cluster).uri(uri)
              .id(i + "").gossipMembers(startupMembers).gossipSettings(settings).build();
      clients.add(gossipService);
      gossipService.getLockManager().setNumberOfNodes(clusterMembers);
      gossipService.init();
      register(gossipService);
    }

    // Adding new data to Node 1
    clients.get(0).gossipSharedData(sharedNodeData("category", "distributed"));

    final AtomicInteger lockSuccessCount = new AtomicInteger(0);
    final AtomicInteger lockFailedCount = new AtomicInteger(0);

    // Node 1 try to lock on key category
    Thread Node1LockingThread = new Thread(() -> {
      try {
        clients.get(0).acquireSharedDataLock("category");
        lockSuccessCount.incrementAndGet();
      } catch (VoteFailedException ignore) {
        lockFailedCount.incrementAndGet();
      }
    });

    // Node 3 try to lock on key category
    Thread Node3LockingThread = new Thread(() -> {
      try {
        clients.get(2).acquireSharedDataLock("category");
        lockSuccessCount.incrementAndGet();
      } catch (VoteFailedException ignore) {
        lockFailedCount.incrementAndGet();
      }
    });

    // Node 6 try to lock on key category
    Thread Node5LockingThread = new Thread(() -> {
      try {
        clients.get(5).acquireSharedDataLock("category");
        lockSuccessCount.incrementAndGet();
      } catch (VoteFailedException ignore) {
        lockFailedCount.incrementAndGet();
      }
    });

    Node1LockingThread.start();
    Node3LockingThread.start();
    Node5LockingThread.start();

    Node1LockingThread.join();
    Node3LockingThread.join();
    Node5LockingThread.join();

    // Only one node should acquire the lock
    Assert.assertEquals(1, lockSuccessCount.get());
    // Other nodes should fail
    Assert.assertEquals(2, lockFailedCount.get());

  }

  private SharedDataMessage sharedNodeData(String key, String value) {
    SharedDataMessage g = new SharedDataMessage();
    g.setExpireAt(Long.MAX_VALUE);
    g.setKey(key);
    g.setPayload(value);
    g.setTimestamp(TimeUtils.getClock().currentTimeMillis());
    return g;
  }

}
