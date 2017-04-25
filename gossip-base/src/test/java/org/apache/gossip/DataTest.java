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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.gossip.crdt.GrowOnlyCounter;
import org.apache.gossip.crdt.GrowOnlySet;
import org.apache.gossip.crdt.OrSet;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.manager.GossipManagerBuilder;
import org.apache.gossip.model.PerNodeDataMessage;
import org.apache.gossip.model.SharedDataMessage;
import org.junit.Test;

import io.teknek.tunit.TUnit;

public class DataTest extends AbstractIntegrationBase {
  
  private String orSetKey = "cror";
  private String gCounterKey = "crdtgc";
  
  @Test
  public void dataTest() throws InterruptedException, UnknownHostException, URISyntaxException{
    GossipSettings settings = new GossipSettings();
    settings.setPersistRingState(false);
    settings.setPersistDataState(false);
    settings.setTransportManagerClass("org.apache.gossip.transport.UnitTestTransportManager");
    settings.setProtocolManagerClass("org.apache.gossip.protocol.UnitTestProtocolManager");
    String cluster = UUID.randomUUID().toString();
    int seedNodes = 1;
    List<Member> startupMembers = new ArrayList<>();
    for (int i = 1; i < seedNodes+1; ++i) {
      URI uri = new URI("udp://" + "127.0.0.1" + ":" + (50000 + i));
      startupMembers.add(new Member(cluster, uri, i + ""));
    }
    final List<GossipManager> clients = new ArrayList<>();
    final int clusterMembers = 2;
    for (int i = 1; i < clusterMembers + 1; ++i) {
      URI uri = new URI("udp://" + "127.0.0.1" + ":" + (50000 + i));
      GossipManager gossipService = GossipManagerBuilder.newBuilder().cluster(cluster).uri(uri)
              .id(i + "").gossipMembers(startupMembers).gossipSettings(settings).build();
      clients.add(gossipService);
      gossipService.init();
      register(gossipService);
    }
    TUnit.assertThat(() -> {
      int total = 0;
      for (int i = 0; i < clusterMembers; ++i) {
        total += clients.get(i).getLiveMembers().size();
      }
      return total;
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo(2);
    clients.get(0).gossipPerNodeData(msg());
    clients.get(0).gossipSharedData(sharedMsg());

    TUnit.assertThat(()-> {
      PerNodeDataMessage x = clients.get(1).findPerNodeGossipData(1 + "", "a");
      if (x == null)
        return "";
      else
        return x.getPayload();
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo("b");
   
    TUnit.assertThat(() ->  {
      SharedDataMessage x = clients.get(1).findSharedGossipData("a");
      if (x == null)
        return "";
      else
        return x.getPayload();
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo("c");
    
    
    givenDifferentDatumsInSet(clients);
    assertThatListIsMerged(clients);
    
    givenOrs(clients);
    assertThatOrSetIsMerged(clients);
    dropIt(clients);
    assertThatOrSetDelIsMerged(clients);

    
    // test g counter
    givenDifferentIncrement(clients);
    assertThatCountIsUpdated(clients, 3);
    givenIncreaseOther(clients);
    assertThatCountIsUpdated(clients, 7);

    for (int i = 0; i < clusterMembers; ++i) {
      clients.get(i).shutdown();
    }
  }
  
  private void givenDifferentIncrement(final List<GossipManager> clients) {
    {
      SharedDataMessage d = new SharedDataMessage();
      d.setKey(gCounterKey);
      d.setPayload(new GrowOnlyCounter(new GrowOnlyCounter.Builder(clients.get(0)).increment(1L)));
      d.setExpireAt(Long.MAX_VALUE);
      d.setTimestamp(System.currentTimeMillis());
      clients.get(0).merge(d);
    }
    {
      SharedDataMessage d = new SharedDataMessage();
      d.setKey(gCounterKey);
      d.setPayload(new GrowOnlyCounter(new GrowOnlyCounter.Builder(clients.get(1)).increment(2L)));
      d.setExpireAt(Long.MAX_VALUE);
      d.setTimestamp(System.currentTimeMillis());
      clients.get(1).merge(d);
    }
  }

  private void givenIncreaseOther(final List<GossipManager> clients) {
    GrowOnlyCounter gc = (GrowOnlyCounter) clients.get(1).findCrdt(gCounterKey);
    GrowOnlyCounter gc2 = new GrowOnlyCounter(gc,
            new GrowOnlyCounter.Builder(clients.get(1)).increment(4L));

    SharedDataMessage d = new SharedDataMessage();
    d.setKey(gCounterKey);
    d.setPayload(gc2);
    d.setExpireAt(Long.MAX_VALUE);
    d.setTimestamp(System.currentTimeMillis());
    clients.get(1).merge(d);
  }

  private void givenOrs(List<GossipManager> clients) {
    {
      SharedDataMessage d = new SharedDataMessage();
      d.setKey(orSetKey);
      d.setPayload(new OrSet<String>("1", "2"));
      d.setExpireAt(Long.MAX_VALUE);
      d.setTimestamp(System.currentTimeMillis());
      clients.get(0).merge(d);
    }
    {
      SharedDataMessage d = new SharedDataMessage();
      d.setKey(orSetKey);
      d.setPayload(new OrSet<String>("3", "4"));
      d.setExpireAt(Long.MAX_VALUE);
      d.setTimestamp(System.currentTimeMillis());
      clients.get(1).merge(d);
    }
  }
  
  private void dropIt(List<GossipManager> clients) {
    @SuppressWarnings("unchecked")
    OrSet<String> o = (OrSet<String>) clients.get(0).findCrdt(orSetKey);
    OrSet<String> o2 = new OrSet<String>(o, new OrSet.Builder<String>().remove("3"));
    SharedDataMessage d = new SharedDataMessage();
    d.setKey(orSetKey);
    d.setPayload(o2);
    d.setExpireAt(Long.MAX_VALUE);
    d.setTimestamp(System.currentTimeMillis());
    clients.get(0).merge(d);
  }
  
  private void assertThatOrSetIsMerged(final List<GossipManager> clients){
    TUnit.assertThat(() ->  {
      return clients.get(0).findCrdt(orSetKey).value();
    }).afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo(new OrSet<String>("1", "2", "3", "4").value());
    TUnit.assertThat(() ->  {
      return clients.get(1).findCrdt(orSetKey).value();
    }).afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo(new OrSet<String>("1", "2", "3", "4").value());
  }
  
  private void assertThatOrSetDelIsMerged(final List<GossipManager> clients){
    TUnit.assertThat(() ->  {
      return clients.get(0).findCrdt(orSetKey);
    }).afterWaitingAtMost(10, TimeUnit.SECONDS).equals(new OrSet<String>("1", "2", "4"));
  }

  private void givenDifferentDatumsInSet(final List<GossipManager> clients){
    clients.get(0).merge(CrdtMessage("1"));
    clients.get(1).merge(CrdtMessage("2"));
  }
  

  private void assertThatCountIsUpdated(final List<GossipManager> clients, long finalCount) {
    TUnit.assertThat(() -> {
      return clients.get(0).findCrdt(gCounterKey);
    }).afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo(new GrowOnlyCounter(
            new GrowOnlyCounter.Builder(clients.get(0)).increment(finalCount)));
  }

  private void assertThatListIsMerged(final List<GossipManager> clients){
    TUnit.assertThat(() ->  {
      return clients.get(0).findCrdt("cr");
    }).afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo(new GrowOnlySet<String>(Arrays.asList("1","2")));
  }
  
  private SharedDataMessage CrdtMessage(String item){
    SharedDataMessage d = new SharedDataMessage();
    d.setKey("cr");
    d.setPayload(new GrowOnlySet<String>( Arrays.asList(item)));
    d.setExpireAt(Long.MAX_VALUE);
    d.setTimestamp(System.currentTimeMillis());
    return d;
  }
  
  private PerNodeDataMessage msg(){
    PerNodeDataMessage g = new PerNodeDataMessage();
    g.setExpireAt(Long.MAX_VALUE);
    g.setKey("a");
    g.setPayload("b");
    g.setTimestamp(System.currentTimeMillis());
    return g;
  }
  
  private SharedDataMessage sharedMsg(){
    SharedDataMessage g = new SharedDataMessage();
    g.setExpireAt(Long.MAX_VALUE);
    g.setKey("a");
    g.setPayload("c");
    g.setTimestamp(System.currentTimeMillis());
    return g;
  }
  
}
