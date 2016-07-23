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

import io.teknek.tunit.TUnit;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;


import org.apache.log4j.Logger;
import org.junit.Test;

import org.apache.gossip.GossipMember;
import org.apache.gossip.GossipService;
import org.apache.gossip.GossipSettings;
import org.apache.gossip.RemoteGossipMember;
import org.apache.gossip.event.GossipListener;
import org.apache.gossip.event.GossipState;

public class ShutdownDeadtimeTest {

  private static final Logger log = Logger.getLogger(ShutdownDeadtimeTest.class );
  @Test
  //@Ignore
  public void DeadNodesDoNotComeAliveAgain() throws InterruptedException, UnknownHostException, URISyntaxException {
      GossipSettings settings = new GossipSettings(1000, 10000);
      String cluster = UUID.randomUUID().toString();
      
      log.info( "Adding seed nodes" );
      int seedNodes = 3;
      List<GossipMember> startupMembers = new ArrayList<>();
      for (int i = 1; i < seedNodes + 1; ++i) {
        URI uri = new URI("udp://" + "127.0.0.1" + ":" + (50000 + i));
        startupMembers.add(new RemoteGossipMember(cluster, uri, i + ""));
      }

      log.info( "Adding clients" );
      final List<GossipService> clients = new ArrayList<>();
      final int clusterMembers = 5;
      for (int i = 1; i < clusterMembers+1; ++i) {
          final int j = i;
          URI uri = new URI("udp://" + "127.0.0.1" + ":" + (50000 + i));
          GossipService gossipService = new GossipService(cluster, uri, i + "",
                  startupMembers, settings,
                  new GossipListener(){
                      @Override
                      public void gossipEvent(GossipMember member, GossipState state) {
                          System.out.println(System.currentTimeMillis() + " Member "+j + " reports "+ member+" "+ state);
                      }
                  });
          clients.add(gossipService);
          gossipService.start();
      }
      TUnit.assertThat(new Callable<Integer> (){
          public Integer call() throws Exception {
              int total = 0;
              for (int i = 0; i < clusterMembers; ++i) {
                  total += clients.get(i).get_gossipManager().getMemberList().size();
              }
              return total;
          }}).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo(20);

      // shutdown one client and verify that one client is lost.
      Random r = new Random();
      int randomClientId = r.nextInt(clusterMembers);
      log.info( "shutting down " + randomClientId );
      final int shutdownPort = clients.get(randomClientId).get_gossipManager().getMyself().getUri().getPort();
      final String shutdownId = clients.get(randomClientId).get_gossipManager().getMyself().getId();
      clients.get(randomClientId).shutdown();
      TUnit.assertThat(new Callable<Integer> (){
          public Integer call() throws Exception {
              int total = 0;
              for (int i = 0; i < clusterMembers; ++i) {
                  total += clients.get(i).get_gossipManager().getMemberList().size();
              }
              return total;
          }}).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo(16);
      clients.remove(randomClientId);
      
      TUnit.assertThat(new Callable<Integer> (){
        public Integer call() throws Exception {
            int total = 0;
            for (int i = 0; i < clusterMembers - 1; ++i) {
                total += clients.get(i).get_gossipManager().getDeadList().size();
            }
            return total;
        }}).afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo(4);
      
      URI uri = new URI("udp://" + "127.0.0.1" + ":" + shutdownPort);
      // start client again
      GossipService gossipService = new GossipService(cluster, uri, shutdownId + "",
              startupMembers, settings,
              new GossipListener(){
                  @Override
                  public void gossipEvent(GossipMember member, GossipState state) {
                      //System.out.println("revived " + member+" "+ state);
                  }
              });
      clients.add(gossipService);
      gossipService.start();

      // verify that the client is alive again for every node
      TUnit.assertThat(new Callable<Integer> (){
          public Integer call() throws Exception {
              int total = 0;
              for (int i = 0; i < clusterMembers; ++i) {
                  total += clients.get(i).get_gossipManager().getMemberList().size();
              }
              return total;
          }}).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo(20);
      
      for (int i = 0; i < clusterMembers; ++i) {
        clients.get(i).shutdown();
      }
  }
}
