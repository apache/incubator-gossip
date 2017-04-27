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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import org.apache.gossip.LocalMember;
import org.apache.gossip.model.ActiveGossipOk;
import org.apache.gossip.model.PerNodeDataMessage;
import org.apache.gossip.model.Member;
import org.apache.gossip.model.Response;
import org.apache.gossip.model.SharedDataMessage;
import org.apache.gossip.model.ShutdownMessage;
import org.apache.gossip.udp.UdpActiveGossipMessage;
import org.apache.gossip.udp.UdpPerNodeDataMessage;
import org.apache.gossip.udp.UdpSharedDataMessage;
import org.apache.log4j.Logger;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * The ActiveGossipper sends information. You can use gossip strategy instances to control how gossip happens.
 */
public class ActiveGossiper {

  protected static final Logger LOGGER = Logger.getLogger(ActiveGossiper.class);
  
  
  private final ClusterModel gossipManager;
  private final MessagingManager messagingManager;
  private final Histogram sharedDataHistogram;
  private final Histogram sendPerNodeDataHistogram;
  private final Histogram sendMembershipHistorgram;
  private static final Random random = new Random(System.nanoTime());
  
  private final ScheduledExecutorService scheduledExecutorService;
  private final BlockingQueue<Runnable> workQueue;
  private final ThreadPoolExecutor threadService;

  public ActiveGossiper(ClusterModel gossipManager, MessagingManager messagingManager, MetricRegistry registry) {
    this.gossipManager = gossipManager;
    this.messagingManager = messagingManager;
    sharedDataHistogram = registry.histogram(name(ActiveGossiper.class, "sharedDataHistogram-time"));
    sendPerNodeDataHistogram = registry.histogram(name(ActiveGossiper.class, "sendPerNodeDataHistogram-time"));
    sendMembershipHistorgram = registry.histogram(name(ActiveGossiper.class, "sendMembershipHistorgram-time"));
    scheduledExecutorService = Executors.newScheduledThreadPool(2);
    workQueue = new ArrayBlockingQueue<Runnable>(1024);
    threadService = new ThreadPoolExecutor(1, 30, 1, TimeUnit.SECONDS, workQueue,
            new ThreadPoolExecutor.DiscardOldestPolicy());
  }
  
  public void schedule(Runnable r, long periodMillis) {
    scheduledExecutorService.scheduleAtFixedRate(() ->
        threadService.execute(r),
        0, periodMillis, TimeUnit.MILLISECONDS
    );
  }

  public void shutdown() {
    scheduledExecutorService.shutdown();
    try {
      scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOGGER.debug("Issue during shutdown", e);
    }
    List<LocalMember> l = gossipManager.getLiveMembers();
    // sends an optimistic shutdown message to several clusters nodes
    int sendTo = l.size() < 3 ? 1 : l.size() / 3;
    for (int i = 0; i < sendTo; i++) {
      threadService.execute(() -> sendShutdownMessage(gossipManager.getMyself(), messagingManager.getNanoTime(), selectPartner(l)));
    }
    threadService.shutdown();
    try {
      threadService.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOGGER.debug("Issue during shutdown", e);
    }
  }

  private void sendShutdownMessage(LocalMember me, long when, LocalMember target){
    if (target == null){
      return;
    }
    ShutdownMessage m = new ShutdownMessage();
    m.setNodeId(me.getId());
    m.setShutdownAtNanos(when);
    messagingManager.sendOneWay(m, target.getUri());
  }
  
  public final void sendSharedData(LocalMember me, LocalMember member){
    if (member == null){
      return;
    }
    long startTime = System.currentTimeMillis();
    for (SharedDataMessage innerEntry : gossipManager.getSharedData()) {
      UdpSharedDataMessage message = new UdpSharedDataMessage();
      message.setUuid(UUID.randomUUID().toString());
      message.setUriFrom(me.getId());
      message.setExpireAt(innerEntry.getExpireAt());
      message.setKey(innerEntry.getKey());
      message.setNodeId(innerEntry.getNodeId());
      message.setTimestamp(innerEntry.getTimestamp());
      message.setPayload(innerEntry.getPayload());
      messagingManager.sendOneWay(message, member.getUri());
    }
    sharedDataHistogram.update(System.currentTimeMillis() - startTime);
  }
  
  public final void sendPerNodeData(LocalMember me, LocalMember member){
    if (member == null){
      return;
    }
    long startTime = System.currentTimeMillis();
    for (PerNodeDataMessage innerEntry : gossipManager.getPerNodeData()) {
      UdpPerNodeDataMessage message = new UdpPerNodeDataMessage();
      message.setUuid(UUID.randomUUID().toString());
      message.setUriFrom(me.getId());
      message.setExpireAt(innerEntry.getExpireAt());
      message.setKey(innerEntry.getKey());
      message.setNodeId(innerEntry.getNodeId());
      message.setTimestamp(innerEntry.getTimestamp());
      message.setPayload(innerEntry.getPayload());
      messagingManager.sendOneWay(message, member.getUri());
    }
    sendPerNodeDataHistogram.update(System.currentTimeMillis() - startTime);
  }
    
  /**
   * Performs the sending of the membership list, after we have incremented our own heartbeat.
   */
  protected void sendMembershipList(LocalMember me, LocalMember member) {
    if (member == null){
      return;
    }
    long startTime = System.currentTimeMillis();
    me.setHeartbeat(System.nanoTime());
    UdpActiveGossipMessage message = new UdpActiveGossipMessage();
    message.setUriFrom(gossipManager.getMyself().getUri().toASCIIString());
    message.setUuid(UUID.randomUUID().toString());
    message.getMembers().add(convert(me));
    for (LocalMember other : gossipManager.getMembers().keySet()) {
      message.getMembers().add(convert(other));
    }
    Response r = messagingManager.send(message, member.getUri());
    if (r instanceof ActiveGossipOk){
      //maybe count metrics here
    } else {
      LOGGER.debug("Message " + message + " generated response " + r);
    }
    sendMembershipHistorgram.update(System.currentTimeMillis() - startTime);
  }
    
  private static Member convert(LocalMember member){
    Member gm = new Member();
    gm.setCluster(member.getClusterName());
    gm.setHeartbeat(member.getHeartbeat());
    gm.setUri(member.getUri().toASCIIString());
    gm.setId(member.getId());
    gm.setProperties(member.getProperties());
    return gm;
  }
  
  /**
   * 
   * @param memberList
   *          An immutable list
   * @return The chosen LocalGossipMember to gossip with.
   */
  public static LocalMember selectPartner(List<LocalMember> memberList) {
    LocalMember member = null;
    if (memberList.size() > 0) {
      int randomNeighborIndex = random.nextInt(memberList.size());
      member = memberList.get(randomNeighborIndex);
    }
    return member;
  }
}
