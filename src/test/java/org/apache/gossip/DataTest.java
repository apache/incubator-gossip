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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.gossip.crdt.GrowOnlyCounter;
import org.apache.gossip.crdt.GrowOnlySet;
import org.apache.gossip.crdt.OrSet;
import org.apache.gossip.model.GossipDataMessage;
import org.apache.gossip.model.SharedGossipDataMessage;
import org.junit.Test;

import io.teknek.tunit.TUnit;

public class DataTest {

  private String orSetKey = "cror";
  private String gCounterKey = "crdtgc";

  @Test
  public void dataTest() throws InterruptedException, UnknownHostException, URISyntaxException{
    GossipSettings settings = new GossipSettings();
    settings.setPersistRingState(false);
    settings.setPersistDataState(false);
    String cluster = UUID.randomUUID().toString();
    int seedNodes = 1;
    List<GossipMember> startupMembers = new ArrayList<>();
    for (int i = 1; i < seedNodes+1; ++i) {
      URI uri = new URI("udp://" + "127.0.0.1" + ":" + (50000 + i));
      startupMembers.add(new RemoteGossipMember(cluster, uri, i + ""));
    }
    final List<GossipService> clients = new ArrayList<>();
    final int clusterMembers = 2;
    for (int i = 1; i < clusterMembers+1; ++i) {
      URI uri = new URI("udp://" + "127.0.0.1" + ":" + (50000 + i));
      GossipService gossipService = new GossipService(cluster, uri, i + "",
              new HashMap<String,String>(), startupMembers, settings,
              (a,b) -> {}, new MetricRegistry());
      clients.add(gossipService);
      gossipService.start();
    }
    TUnit.assertThat(() -> {
      int total = 0;
      for (int i = 0; i < clusterMembers; ++i) {
        total += clients.get(i).getGossipManager().getLiveMembers().size();
      }
      return total;
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo(2);
    clients.get(0).gossipPerNodeData(msg());
    clients.get(0).gossipSharedData(sharedMsg());

    TUnit.assertThat(()-> {
      GossipDataMessage x = clients.get(1).findPerNodeData(1 + "", "a");
      if (x == null)
        return "";
      else
        return x.getPayload();
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo("b");

    TUnit.assertThat(() ->  {
      SharedGossipDataMessage x = clients.get(1).findSharedData("a");
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
    assertThatCountIsUpdated(clients,3);
    givenIncreaseOther(clients);
    assertThatCountIsUpdated(clients,7);

    for (int i = 0; i < clusterMembers; ++i) {
      clients.get(i).shutdown();
    }
  }


  private void givenDifferentIncrement(final List<GossipService> clients) {
    {
      SharedGossipDataMessage d = new SharedGossipDataMessage();
      d.setKey(gCounterKey);
      d.setPayload(new GrowOnlyCounter(new GrowOnlyCounter.Builder(clients.get(0).getGossipManager()).increment(1)));
      d.setExpireAt(Long.MAX_VALUE);
      d.setTimestamp(System.currentTimeMillis());
      clients.get(0).getGossipManager().merge(d);
    }
    {
      SharedGossipDataMessage d = new SharedGossipDataMessage();
      d.setKey(gCounterKey);
      d.setPayload(new GrowOnlyCounter(new GrowOnlyCounter.Builder(clients.get(1).getGossipManager()).increment(2)));
      d.setExpireAt(Long.MAX_VALUE);
      d.setTimestamp(System.currentTimeMillis());
      clients.get(1).getGossipManager().merge(d);
    }
  }

  private void givenIncreaseOther(final List<GossipService> clients) {
		GrowOnlyCounter gc = (GrowOnlyCounter) clients.get(1).getGossipManager().findCrdt(gCounterKey);
		GrowOnlyCounter gc2 = new GrowOnlyCounter(gc,
						new GrowOnlyCounter.Builder(clients.get(1).getGossipManager()).increment(4));

		SharedGossipDataMessage d = new SharedGossipDataMessage();
		d.setKey(gCounterKey);
		d.setPayload(gc2);
		d.setExpireAt(Long.MAX_VALUE);
		d.setTimestamp(System.currentTimeMillis());
		clients.get(1).getGossipManager().merge(d);
  }

  private void givenOrs(List<GossipService> clients) {
    {
      SharedGossipDataMessage d = new SharedGossipDataMessage();
      d.setKey(orSetKey);
      d.setPayload(new OrSet<String>("1", "2"));
      d.setExpireAt(Long.MAX_VALUE);
      d.setTimestamp(System.currentTimeMillis());
      clients.get(0).getGossipManager().merge(d);
    }
    {
      SharedGossipDataMessage d = new SharedGossipDataMessage();
      d.setKey(orSetKey);
      d.setPayload(new OrSet<String>("3", "4"));
      d.setExpireAt(Long.MAX_VALUE);
      d.setTimestamp(System.currentTimeMillis());
      clients.get(1).getGossipManager().merge(d);
    }
  }

  private void dropIt(List<GossipService> clients) {
    @SuppressWarnings("unchecked")
    OrSet<String> o = (OrSet<String>) clients.get(0).getGossipManager().findCrdt(orSetKey);
    OrSet<String> o2 = new OrSet<String>(o, new OrSet.Builder<String>().remove("3"));
    SharedGossipDataMessage d = new SharedGossipDataMessage();
    d.setKey(orSetKey);
    d.setPayload(o2);
    d.setExpireAt(Long.MAX_VALUE);
    d.setTimestamp(System.currentTimeMillis());
    clients.get(0).getGossipManager().merge(d);
  }

  private void assertThatOrSetIsMerged(final List<GossipService> clients){
    TUnit.assertThat(() ->  {
      return clients.get(0).getGossipManager().findCrdt(orSetKey).value();
    }).afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo(new OrSet<String>("1", "2", "3", "4").value());
    TUnit.assertThat(() ->  {
      return clients.get(1).getGossipManager().findCrdt(orSetKey).value();
    }).afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo(new OrSet<String>("1", "2", "3", "4").value());
  }

  private void assertThatOrSetDelIsMerged(final List<GossipService> clients){
    TUnit.assertThat(() ->  {
      return clients.get(0).getGossipManager().findCrdt(orSetKey);
    }).afterWaitingAtMost(10, TimeUnit.SECONDS).equals(new OrSet<String>("1", "2", "4"));
  }

  private void givenDifferentDatumsInSet(final List<GossipService> clients){
    clients.get(0).getGossipManager().merge(CrdtMessage("1"));
    clients.get(1).getGossipManager().merge(CrdtMessage("2"));
  }

  private void assertThatCountIsUpdated(final List<GossipService> clients, int finalCount){
		TUnit.assertThat(() -> {
			return clients.get(0).getGossipManager().findCrdt(gCounterKey);
		}).afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo(new GrowOnlyCounter(
						new GrowOnlyCounter.Builder(clients.get(0).getGossipManager()).increment(finalCount)));
  }

  private void assertThatListIsMerged(final List<GossipService> clients){
    TUnit.assertThat(() ->  {
      return clients.get(0).getGossipManager().findCrdt("cr");
    }).afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo(new GrowOnlySet<String>(Arrays.asList("1","2")));
  }

  private SharedGossipDataMessage CrdtMessage(String item){
    SharedGossipDataMessage d = new SharedGossipDataMessage();
    d.setKey("cr");
    d.setPayload(new GrowOnlySet<String>( Arrays.asList(item)));
    d.setExpireAt(Long.MAX_VALUE);
    d.setTimestamp(System.currentTimeMillis());
    return d;
  }

  private GossipDataMessage msg(){
    GossipDataMessage g = new GossipDataMessage();
    g.setExpireAt(Long.MAX_VALUE);
    g.setKey("a");
    g.setPayload("b");
    g.setTimestamp(System.currentTimeMillis());
    return g;
  }

  private SharedGossipDataMessage sharedMsg(){
    SharedGossipDataMessage g = new SharedGossipDataMessage();
    g.setExpireAt(Long.MAX_VALUE);
    g.setKey("a");
    g.setPayload("c");
    g.setTimestamp(System.currentTimeMillis());
    return g;
  }

}
