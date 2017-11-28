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
import org.apache.gossip.crdt.*;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.manager.GossipManagerBuilder;
import org.apache.gossip.model.PerNodeDataMessage;
import org.apache.gossip.model.SharedDataMessage;
import org.apache.gossip.utils.TimeUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class DataTest {
  private final String gCounterKey = "crdtgc";
  private final String pnCounterKey = "crdtpn";

  private static final List<GossipManager> clients = new ArrayList<>();

  @BeforeClass
  public static void initializeMembers() throws InterruptedException, UnknownHostException, URISyntaxException{
    final int clusterMembers = 2;

    GossipSettings settings = new GossipSettings();
    settings.setPersistRingState(false);
    settings.setPersistDataState(false);
    String cluster = UUID.randomUUID().toString();
    List<Member> startupMembers = new ArrayList<>();
    for (int i = 0; i < clusterMembers; ++i){
      int id = i + 1;
      URI uri = new URI("udp://" + "127.0.0.1" + ":" + (50000 + id));
      startupMembers.add(new RemoteMember(cluster, uri, id + ""));
    }

    for (Member member : startupMembers){
      GossipManager gossipService = GossipManagerBuilder.newBuilder().cluster(cluster).uri(member.getUri())
          .id(member.getId()).gossipMembers(startupMembers).gossipSettings(settings).build();
      clients.add(gossipService);
      gossipService.init();
    }
  }

  @AfterClass
  public static void shutdownMembers(){
    for (final GossipManager client : clients){
      client.shutdown();
    }
  }

  @Test
  public void simpleDataTest(){
    TUnit.assertThat(() -> {
      int total = 0;
      for (GossipManager client : clients){
        total += client.getLiveMembers().size();
      }
      return total;
    }).afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo(2);

    clients.get(0).gossipPerNodeData(generatePerNodeMsg("a", "b"));
    clients.get(0).gossipSharedData(generateSharedMsg("a", "c"));

    TUnit.assertThat(() -> {
      PerNodeDataMessage x = clients.get(1).findPerNodeGossipData(1 + "", "a");
      if (x == null)
        return "";
      else
        return x.getPayload();
    }).afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo("b");

    TUnit.assertThat(() -> {
      SharedDataMessage x = clients.get(1).findSharedGossipData("a");
      if (x == null)
        return "";
      else
        return x.getPayload();
    }).afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo("c");
  }

  Set<String> setFromList(String... elements){
    return new HashSet<>(Arrays.asList(elements));
  }

  void crdtSetTest(String key, Function<Set<String>, CrdtAddRemoveSet<String, Set<String>, ?>> construct){
    //populate
    clients.get(0).merge(generateSharedMsg(key, construct.apply(setFromList("1", "2"))));
    clients.get(1).merge(generateSharedMsg(key, construct.apply(setFromList("3", "4"))));

    assertMergedCrdt(key, construct.apply(setFromList("1", "2", "3", "4")).value());

    //drop element
    @SuppressWarnings("unchecked")
    CrdtAddRemoveSet<String, ?, ?> set = (CrdtAddRemoveSet<String, ?, ?>) clients.get(0).findCrdt(key);
    clients.get(0).merge(generateSharedMsg(key, set.remove("3")));

    //assert deletion
    assertMergedCrdt(key, construct.apply(setFromList("1", "2", "4")).value());
  }

  @Test
  public void OrSetTest(){
    crdtSetTest("cror", OrSet::new);
  }

  @Test
  public void LWWSetTest(){
    crdtSetTest("crlww", LwwSet::new);
  }

  @Test
  public void MaxChangeSetTest(){
    crdtSetTest("crmcs", MaxChangeSet::new);
  }

  @Test
  public void TwoPhaseSetTest(){
    crdtSetTest("crtps", TwoPhaseSet::new);
  }

  @Test
  public void GrowOnlyCounterTest(){
    Consumer<Long> assertCountUpdated = count -> {
      for (GossipManager client : clients){
        TUnit.assertThat(() -> client.findCrdt(gCounterKey))
            .afterWaitingAtMost(10, TimeUnit.SECONDS)
            .isEqualTo(new GrowOnlyCounter(new GrowOnlyCounter.Builder(client).increment(count)));
      }
    };
    //generate different increment
    Object payload = new GrowOnlyCounter(new GrowOnlyCounter.Builder(clients.get(0)).increment(1L));
    clients.get(0).merge(generateSharedMsg(gCounterKey, payload));
    payload = new GrowOnlyCounter(new GrowOnlyCounter.Builder(clients.get(1)).increment(2L));
    clients.get(1).merge(generateSharedMsg(gCounterKey, payload));

    assertCountUpdated.accept((long) 3);

    //update one
    GrowOnlyCounter gc = (GrowOnlyCounter) clients.get(1).findCrdt(gCounterKey);
    GrowOnlyCounter gc2 = new GrowOnlyCounter(gc,
        new GrowOnlyCounter.Builder(clients.get(1)).increment(4L));
    clients.get(1).merge(generateSharedMsg(gCounterKey, gc2));

    assertCountUpdated.accept((long) 7);
  }

  @Test
  public void PNCounterTest(){
    Consumer<List<Integer>> counterUpdate = list -> {
      int clientIndex = 0;
      for (int delta : list){
        PNCounter c = (PNCounter) clients.get(clientIndex).findCrdt(pnCounterKey);
        c = new PNCounter(c, new PNCounter.Builder(clients.get(clientIndex)).increment(((long) delta)));
        clients.get(clientIndex).merge(generateSharedMsg(pnCounterKey, c));
        clientIndex = (clientIndex + 1) % clients.size();
      }
    };

    // given PNCounter
    clients.get(0).merge(generateSharedMsg(pnCounterKey, new PNCounter(new PNCounter.Builder(clients.get(0)))));
    clients.get(1).merge(generateSharedMsg(pnCounterKey, new PNCounter(new PNCounter.Builder(clients.get(1)))));

    assertMergedCrdt(pnCounterKey, (long) 0);

    List<List<Integer>> updateLists = new ArrayList<>();
    updateLists.add(Arrays.asList(2, 3));
    updateLists.add(Arrays.asList(-3, 5));
    updateLists.add(Arrays.asList(1, 1));
    updateLists.add(Arrays.asList(1, -7));

    Long[] expectedResults = {5L, 7L, 9L, 3L};

    for (int i = 0; i < updateLists.size(); i++){
      counterUpdate.accept(updateLists.get(i));
      assertMergedCrdt(pnCounterKey, expectedResults[i]);
    }
  }

  @Test
  public void GrowOnlySetTest(){
    clients.get(0).merge(generateSharedMsg("cr", new GrowOnlySet<>(Arrays.asList("1"))));
    clients.get(1).merge(generateSharedMsg("cr", new GrowOnlySet<>(Arrays.asList("2"))));

    assertMergedCrdt("cr", new GrowOnlySet<>(Arrays.asList("1", "2")).value());
  }

  private void assertMergedCrdt(String key, Object expected){
    for (GossipManager client : clients){
      TUnit.assertThat(() -> client.findCrdt(key).value())
          .afterWaitingAtMost(10, TimeUnit.SECONDS).isEqualTo(expected);
    }
  }

  private PerNodeDataMessage generatePerNodeMsg(String key, Object payload){
    PerNodeDataMessage g = new PerNodeDataMessage();
    g.setExpireAt(Long.MAX_VALUE);
    g.setKey(key);
    g.setPayload(payload);
    g.setTimestamp(TimeUtils.getClock().currentTimeMillis());
    return g;
  }

  private SharedDataMessage generateSharedMsg(String key, Object payload){
    SharedDataMessage d = new SharedDataMessage();
    d.setKey(key);
    d.setPayload(payload);
    d.setExpireAt(Long.MAX_VALUE);
    d.setTimestamp(TimeUtils.getClock().currentTimeMillis());
    return d;
  }
}