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
import org.apache.gossip.manager.DatacenterRackAwareActiveGossiper;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.manager.GossipManagerBuilder;
import org.apache.gossip.model.SharedDataMessage;
import org.apache.gossip.replication.*;
import org.apache.gossip.utils.TimeUtils;
import org.junit.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RunWith(JUnitPlatform.class)
public class SharedDataReplicationControlTest extends AbstractIntegrationBase {

  @Test
  public void sharedDataReplicationTest()
          throws InterruptedException, UnknownHostException, URISyntaxException {
    generateStandardNodes(3);

    // check whether the members are discovered
    TUnit.assertThat(() -> {
      int total = 0;
      for (GossipManager node : nodes) {
        total += node.getLiveMembers().size();
      }
      return total;
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo(2);

    // Adding new shared data to Node 1 with default replication (replicate all)
    nodes.get(0).gossipSharedData(sharedNodeData("public", "I am visible to all",
            new AllReplicable<>()));
    // Adding new shared data to Node 1 with no replication (replicate none)
    nodes.get(0).gossipSharedData(sharedNodeData("private", "I am private",
            new NotReplicable<>()));

    List<LocalMember> whiteList = new ArrayList<>();
    whiteList.add(nodes.get(1).getMyself());
    // Adding new shared data to Node 1 with white list Node 2
    nodes.get(0).gossipSharedData(sharedNodeData("wl", "white list",
            new WhiteListReplicable<>(whiteList)));

    List<LocalMember> blackList = new ArrayList<>();
    blackList.add(nodes.get(1).getMyself());
    // Adding new shared data to Node 1 with black list Node 2
    nodes.get(0).gossipSharedData(sharedNodeData("bl", "black list",
            new BlackListReplicable<>(blackList)));

    TUnit.assertThat(() -> {
      SharedDataMessage message = nodes.get(1).findSharedGossipData("public");
      if(message == null){
        return "";
      }else {
        return message.getPayload();
      }
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo("I am visible to all");

    TUnit.assertThat(() -> {
      SharedDataMessage message = nodes.get(2).findSharedGossipData("public");
      if(message == null){
        return "";
      }else {
        return message.getPayload();
      }
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo("I am visible to all");

    // Node 2 must have shared data with key wl
    TUnit.assertThat(() -> {
      SharedDataMessage message = nodes.get(1).findSharedGossipData("wl");
      if(message == null){
        return "";
      }else {
        return message.getPayload();
      }
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo("white list");

    // Node 3 must have shared data with key bl
    TUnit.assertThat(() -> {
      SharedDataMessage message = nodes.get(2).findSharedGossipData("bl");
      if(message == null){
        return "";
      }else {
        return message.getPayload();
      }
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo("black list");
  }

  @Test
  public void sharedDataDcReplicationTest()
          throws InterruptedException, UnknownHostException, URISyntaxException {

    GossipSettings settings = new GossipSettings();
    settings.setPersistRingState(false);
    settings.setPersistDataState(false);
    String cluster = UUID.randomUUID().toString();
    settings.setActiveGossipClass(DatacenterRackAwareActiveGossiper.class.getName());

    Map<String, String> gossipProps = new HashMap<>();
    gossipProps.put("sameRackGossipIntervalMs", "500");
    gossipProps.put("differentDatacenterGossipIntervalMs", "1000");
    settings.setActiveGossipProperties(gossipProps);

    RemoteMember seeder = new RemoteMember(cluster, URI.create("udp://127.0.0.1:5001"), "1");

    // initialize 2 data centers with each having two racks
    createDcNode(URI.create("udp://127.0.0.1:5001"), "1", settings, seeder, cluster,
            "DataCenter1", "Rack1");
    createDcNode(URI.create("udp://127.0.0.1:5002"), "2", settings, seeder, cluster,
            "DataCenter1", "Rack2");

    createDcNode(URI.create("udp://127.0.0.1:5006"), "6", settings, seeder, cluster,
            "DataCenter2", "Rack1");
    createDcNode(URI.create("udp://127.0.0.1:5007"), "7", settings, seeder, cluster,
            "DataCenter2", "Rack1");

    // check whether the members are discovered
    TUnit.assertThat(() -> {
      int total = 0;
      for (int i = 0; i < 4; ++i) {
        total += nodes.get(i).getLiveMembers().size();
      }
      return total;
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo(12);

    // Node 1 has a shared key with 'Dc1Rack1'
    nodes.get(0).gossipSharedData(sharedNodeData("Dc1Rack1", "I am belong to Dc1",
            new DataCenterReplicable<>()));
    // Node 6 has a shared key with 'Dc2Rack1'
    nodes.get(2).gossipSharedData(sharedNodeData("Dc2Rack1", "I am belong to Dc2",
            new DataCenterReplicable<>()));

    // Node 2 must have the shared data with key 'Dc1Rack1'
    TUnit.assertThat(() -> {
      SharedDataMessage message = nodes.get(1).findSharedGossipData("Dc1Rack1");
      if(message == null){
        return "";
      }else {
        return message.getPayload();
      }
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo("I am belong to Dc1");

    // Node 7 must have the shared data with key 'Dc2Rack1'
    TUnit.assertThat(() -> {
      SharedDataMessage message = nodes.get(3).findSharedGossipData("Dc2Rack1");
      if(message == null){
        return "";
      }else {
        return message.getPayload();
      }
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo("I am belong to Dc2");

  }

  private SharedDataMessage sharedNodeData(String key, String value,
          Replicable<SharedDataMessage> replicable) {
    SharedDataMessage g = new SharedDataMessage();
    g.setExpireAt(Long.MAX_VALUE);
    g.setKey(key);
    g.setPayload(value);
    g.setTimestamp(TimeUtils.getClock().currentTimeMillis());
    g.setReplicable(replicable);
    return g;
  }

  private void createDcNode(URI uri, String id, GossipSettings settings, RemoteMember seeder,
          String cluster, String dataCenter, String rack){
    Map<String, String> props = new HashMap<>();
    props.put(DatacenterRackAwareActiveGossiper.DATACENTER, dataCenter);
    props.put(DatacenterRackAwareActiveGossiper.RACK, rack);

    GossipManager dcNode = GossipManagerBuilder.newBuilder().cluster(cluster).uri(uri).id(id)
            .gossipSettings(settings).gossipMembers(Arrays.asList(seeder)).properties(props)
            .build();
    dcNode.init();
    register(dcNode);
  }

}
