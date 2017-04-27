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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.apache.gossip.Member;
import org.apache.gossip.LocalMember;
import org.apache.gossip.Member;
import org.apache.gossip.crdt.Crdt;
import org.apache.gossip.event.GossipState;
import org.apache.gossip.model.*;
import org.apache.gossip.udp.Trackable;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.*;

/** contains node data (not data about the nodes which belongs ClusterModel). */
public class GossipCore implements GossipCoreConstants {

  private static final Logger LOGGER = Logger.getLogger(GossipCore.class);
  private final ConcurrentHashMap<String, ConcurrentHashMap<String, PerNodeDataMessage>> perNodeData;
  private final ConcurrentHashMap<String, SharedDataMessage> sharedData;
  private final ClusterModel clusterModel;

  public GossipCore(ClusterModel manager, MetricRegistry metrics){
    this.clusterModel = manager;
    perNodeData = new ConcurrentHashMap<>();
    sharedData = new ConcurrentHashMap<>();
    metrics.register(PER_NODE_DATA_SIZE, (Gauge<Integer>)() -> perNodeData.size());
    metrics.register(SHARED_DATA_SIZE, (Gauge<Integer>)() ->  sharedData.size());
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void addSharedData(SharedDataMessage message) {
    while (true){
      SharedDataMessage previous = sharedData.putIfAbsent(message.getKey(), message);
      if (previous == null){
        return;
      }
      if (message.getPayload() instanceof Crdt){
        SharedDataMessage merged = new SharedDataMessage();
        merged.setExpireAt(message.getExpireAt());
        merged.setKey(message.getKey());
        merged.setNodeId(message.getNodeId());
        merged.setTimestamp(message.getTimestamp());
        Crdt mergedCrdt = ((Crdt) previous.getPayload()).merge((Crdt) message.getPayload());
        merged.setPayload(mergedCrdt);
        boolean replaced = sharedData.replace(message.getKey(), previous, merged);
        if (replaced){
          return;
        }
      } else {
        if (previous.getTimestamp() < message.getTimestamp()){
          boolean result = sharedData.replace(message.getKey(), previous, message);
          if (result){
            return;
          }
        } else {
          return;
        }
      }
    }
  }
  
  public void addPerNodeData(PerNodeDataMessage message){
    ConcurrentHashMap<String,PerNodeDataMessage> nodeMap = new ConcurrentHashMap<>();
    nodeMap.put(message.getKey(), message);
    nodeMap = perNodeData.putIfAbsent(message.getNodeId(), nodeMap);
    if (nodeMap != null){
      PerNodeDataMessage current = nodeMap.get(message.getKey());
      if (current == null){
        nodeMap.putIfAbsent(message.getKey(), message);
      } else {
        if (current.getTimestamp() < message.getTimestamp()){
          nodeMap.replace(message.getKey(), current, message);
        }
      }
    }
  }
  
  public Collection<PerNodeDataMessage> getPerNodeData() {
    List<PerNodeDataMessage> list = new ArrayList<>();
    perNodeData.values().forEach(innerMap -> list.addAll(innerMap.values()));
    return list;
  }

  public Collection<SharedDataMessage> getSharedData() {
    return sharedData.values();
  }
  
  
  public void removeExpiredSharedData(long when) {
    for (Entry<String, SharedDataMessage> entry : sharedData.entrySet()){
      if (entry.getValue().getExpireAt() < when){
        sharedData.remove(entry.getKey(), entry.getValue());
      }
    }
  }
  
  public void removeExpiredPerNodeData(long when) {
    for (Entry<String, ConcurrentHashMap<String, PerNodeDataMessage>> node : perNodeData.entrySet()){
      ConcurrentHashMap<String, PerNodeDataMessage> concurrentHashMap = node.getValue();
      for (Entry<String, PerNodeDataMessage> entry : concurrentHashMap.entrySet()){
        if (entry.getValue().getExpireAt() < when){
          concurrentHashMap.remove(entry.getKey(), entry.getValue());
        }
      }
    }
  }

  /**
   * Merge lists from remote members and update heartbeats
   *
   * @param senderMember
   * @param remoteList
   *
   */
  public void mergeLists(Member senderMember, List<Member> remoteList) {
    if (LOGGER.isDebugEnabled()){
      debugState(senderMember, remoteList);
    }
    for (LocalMember i : clusterModel.getDeadMembers()) {
      if (i.getId().equals(senderMember.getId())) {
        LOGGER.debug(clusterModel.getMyself() + " contacted by dead member " + senderMember.getUri());
        i.recordHeartbeat(senderMember.getHeartbeat());
        i.setHeartbeat(senderMember.getHeartbeat());
        //TODO consider forcing an UP here
      }
    }
    for (Member remoteMember : remoteList) {
      if (remoteMember.getId().equals(clusterModel.getMyself().getId())) {
        continue;
      }
      LocalMember aNewMember = new LocalMember(remoteMember.getClusterName(),
      remoteMember.getUri(),
      remoteMember.getId(),
      remoteMember.getHeartbeat(),
      remoteMember.getProperties(),
      clusterModel.getSettings().getWindowSize(),
      clusterModel.getSettings().getMinimumSamples(),
      clusterModel.getSettings().getDistribution());
      aNewMember.recordHeartbeat(remoteMember.getHeartbeat());
      Object result = clusterModel.getMembers().putIfAbsent(aNewMember, GossipState.UP);
      if (result != null){
        for (Entry<LocalMember, GossipState> localMember : clusterModel.getMembers().entrySet()){
          if (localMember.getKey().getId().equals(remoteMember.getId())){
            localMember.getKey().recordHeartbeat(remoteMember.getHeartbeat());
            localMember.getKey().setHeartbeat(remoteMember.getHeartbeat());
            localMember.getKey().setProperties(remoteMember.getProperties());
          }
        }
      }
    }
    if (LOGGER.isDebugEnabled()){
      debugState(senderMember, remoteList);
    }
  }

  private void debugState(Member senderMember,
          List<Member> remoteList){
    LOGGER.warn(
          "-----------------------\n" +
          "Me " + clusterModel.getMyself() + "\n" +
          "Sender " + senderMember + "\n" +
          "RemoteList " + remoteList + "\n" +
          "Live " + clusterModel.getLiveMembers()+ "\n" +
          "Dead " + clusterModel.getDeadMembers()+ "\n" +
          "=======================");
  }

  public PerNodeDataMessage findPerNodeGossipData(String nodeId, String key){
    ConcurrentHashMap<String, PerNodeDataMessage> j = perNodeData.get(nodeId);
    if (j == null){
      return null;
    } else {
      PerNodeDataMessage l = j.get(key);
      if (l == null){
        return null;
      }
      if (l.getExpireAt() != null && l.getExpireAt() < clusterModel.currentTimeMillis()) {
        return null;
      }
      return l;
    }
  }

  public SharedDataMessage findSharedGossipData(String key){
    SharedDataMessage l = sharedData.get(key);
    if (l == null){
      return null;
    }
    if (l.getExpireAt() < clusterModel.currentTimeMillis()){
      return null;
    } else {
      return l;
    }
  }

  @SuppressWarnings("rawtypes")
  private Crdt mergeInternal(SharedDataMessage message) {
    for (;;){
      SharedDataMessage previous = sharedData.putIfAbsent(message.getKey(), message);
      if (previous == null){
        return (Crdt) message.getPayload();
      }
      SharedDataMessage copy = new SharedDataMessage();
      copy.setExpireAt(message.getExpireAt());
      copy.setKey(message.getKey());
      copy.setNodeId(message.getNodeId());
      copy.setTimestamp(message.getTimestamp());
      @SuppressWarnings("unchecked")
      Crdt merged = ((Crdt) previous.getPayload()).merge((Crdt) message.getPayload());
      copy.setPayload(merged);
      boolean replaced = sharedData.replace(message.getKey(), previous, copy);
      if (replaced){
        return merged;
      }
    }
  }
  
  @SuppressWarnings("rawtypes")
  public Crdt findCrdt(String key, long deadline){
    SharedDataMessage l = sharedData.get(key);
    if (l == null){
      return null;
    }
    if (l.getExpireAt() < deadline){
      return null;
    } else {
      return (Crdt) l.getPayload();
    }
  }
  
  @SuppressWarnings("rawtypes")
  public Crdt merge(SharedDataMessage message, String nodeId){
    Objects.nonNull(message.getKey());
    Objects.nonNull(message.getTimestamp());
    Objects.nonNull(message.getPayload());
    message.setNodeId(nodeId);
    if (! (message.getPayload() instanceof Crdt)){
      throw new IllegalArgumentException("Not a subclass of CRDT " + message.getPayload());
    }
    return mergeInternal(message);
  }
}
