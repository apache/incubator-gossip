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

import com.codahale.metrics.MetricRegistry;
import io.teknek.tunit.TUnit;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import org.junit.platform.runner.JUnitPlatform;
import org.junit.jupiter.api.Test;

import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
public class ShutdownDeadtimeTest {

  private static final Logger log = Logger.getLogger(ShutdownDeadtimeTest.class);
  
  @Test
  public void DeadNodesDoNotComeAliveAgain()
          throws InterruptedException, UnknownHostException, URISyntaxException {
    GossipSettings settings = new GossipSettings(1000, 10000, 1000, 1, 5.0);
    String cluster = UUID.randomUUID().toString();
    int seedNodes = 3;
    List<GossipMember> startupMembers = new ArrayList<>();
    for (int i = 1; i < seedNodes + 1; ++i) {
      URI uri = new URI("udp://" + "127.0.0.1" + ":" + (30300 + i));
      startupMembers.add(new RemoteGossipMember(cluster, uri, i + ""));
    }
    final List<GossipService> clients = Collections.synchronizedList(new ArrayList<GossipService>());
    final int clusterMembers = 5;
    for (int i = 1; i < clusterMembers + 1; ++i) {
      URI uri = new URI("udp://" + "127.0.0.1" + ":" + (30300 + i));
      GossipService gossipService = new GossipService(cluster, uri, i + "", startupMembers,
              settings, (a,b) -> {}, new MetricRegistry());
      clients.add(gossipService);
      gossipService.start();
    }
    TUnit.assertThat(new Callable<Integer>() {
      public Integer call() throws Exception {
        int total = 0;
        for (int i = 0; i < clusterMembers; ++i) {
          total += clients.get(i).getGossipManager().getLiveMembers().size();
        }
        return total;
      }
    }).afterWaitingAtMost(40, TimeUnit.SECONDS).isEqualTo(20);

    // shutdown one client and verify that one client is lost.
    Random r = new Random();
    int randomClientId = r.nextInt(clusterMembers);
    log.info("shutting down " + randomClientId);
    final int shutdownPort = clients.get(randomClientId).getGossipManager().getMyself().getUri()
            .getPort();
    final String shutdownId = clients.get(randomClientId).getGossipManager().getMyself().getId();
    clients.get(randomClientId).shutdown();
    TUnit.assertThat(new Callable<Integer>() {
      public Integer call() throws Exception {
        int total = 0;
        for (int i = 0; i < clusterMembers; ++i) {
          total += clients.get(i).getGossipManager().getLiveMembers().size();
        }
        return total;
      }
    }).afterWaitingAtMost(40, TimeUnit.SECONDS).isEqualTo(16);
    clients.remove(randomClientId);

    TUnit.assertThat(new Callable<Integer>() {
      public Integer call() throws Exception {
        int total = 0;
        for (int i = 0; i < clusterMembers - 1; ++i) {
          total += clients.get(i).getGossipManager().getDeadMembers().size();
        }
        return total;
      }
    }).afterWaitingAtMost(30, TimeUnit.SECONDS).isEqualTo(4);

    URI uri = new URI("udp://" + "127.0.0.1" + ":" + shutdownPort);
    // start client again
    GossipService gossipService = new GossipService(cluster, uri, shutdownId + "", startupMembers,
            settings, (a,b) -> {}, new MetricRegistry());
    clients.add(gossipService);
    gossipService.start();

    // verify that the client is alive again for every node
    TUnit.assertThat(new Callable<Integer>() {
      public Integer call() throws Exception {
        int total = 0;
        for (int i = 0; i < clusterMembers; ++i) {
          total += clients.get(i).getGossipManager().getLiveMembers().size();
        }
        return total;
      }
    }).afterWaitingAtMost(60, TimeUnit.SECONDS).isEqualTo(20);

    for (int i = 0; i < clusterMembers; ++i) {
      clients.get(i).shutdown();
    }
  }
}
