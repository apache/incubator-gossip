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

import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import org.apache.gossip.LocalGossipMember;
import org.apache.gossip.model.ActiveGossipOk;
import org.apache.gossip.model.GossipDataMessage;
import org.apache.gossip.model.GossipMember;
import org.apache.gossip.model.Response;
import org.apache.gossip.model.SharedGossipDataMessage;
import org.apache.gossip.udp.UdpActiveGossipMessage;
import org.apache.gossip.udp.UdpGossipDataMessage;
import org.apache.gossip.udp.UdpSharedGossipDataMessage;
import org.apache.log4j.Logger;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * The ActiveGossipThread is sends information. Pick a random partner and send the membership list to that partner
 */
public class ActiveGossipThread {

  private static final Logger LOGGER = Logger.getLogger(ActiveGossipThread.class);
  
  private final GossipManager gossipManager;
  private final Random random;
  private final GossipCore gossipCore;
  private ScheduledExecutorService scheduledExecutorService;
  private final BlockingQueue<Runnable> workQueue;
  private ThreadPoolExecutor threadService;

  private final Histogram sharedDataHistogram;
  private final Histogram sendPerNodeDataHistogram;
  private final Histogram sendMembershipHistorgram;

  public ActiveGossipThread(GossipManager gossipManager, GossipCore gossipCore, MetricRegistry registry) {
    this.gossipManager = gossipManager;
    random = new Random();
    this.gossipCore = gossipCore;
    scheduledExecutorService = Executors.newScheduledThreadPool(2);
    workQueue = new ArrayBlockingQueue<Runnable>(1024);
    threadService = new ThreadPoolExecutor(1, 30, 1, TimeUnit.SECONDS, workQueue, new ThreadPoolExecutor.DiscardOldestPolicy());
    sharedDataHistogram = registry.histogram(name(ActiveGossipThread.class, "sharedDataHistogram-time"));
    sendPerNodeDataHistogram = registry.histogram(name(ActiveGossipThread.class, "sendPerNodeDataHistogram-time"));
    sendMembershipHistorgram = registry.histogram(name(ActiveGossipThread.class, "sendMembershipHistorgram-time"));
  }


  public void init() {
    scheduledExecutorService.scheduleAtFixedRate(
            () -> { 
              threadService.execute( () -> { sendToALiveMember(); });
            }, 0,
            gossipManager.getSettings().getGossipInterval(), TimeUnit.MILLISECONDS);
    scheduledExecutorService.scheduleAtFixedRate(
            () -> { this.sendToDeadMember(); }, 0,
            gossipManager.getSettings().getGossipInterval(), TimeUnit.MILLISECONDS);
    scheduledExecutorService.scheduleAtFixedRate(
            () -> sendPerNodeData(gossipManager.getMyself(), gossipManager.getLiveMembers()), 0,
            gossipManager.getSettings().getGossipInterval(), TimeUnit.MILLISECONDS);
    scheduledExecutorService.scheduleAtFixedRate(
            () -> sendSharedData(gossipManager.getMyself(), gossipManager.getLiveMembers()), 0,
            gossipManager.getSettings().getGossipInterval(), TimeUnit.MILLISECONDS);
  }
  
  public void shutdown() {
    scheduledExecutorService.shutdown();
    try {
      scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOGGER.debug("Issue during shurdown" + e);
    }
  }

  public void sendSharedData(LocalGossipMember me, List<LocalGossipMember> memberList){
    long startTime = System.currentTimeMillis();

    LocalGossipMember member = selectPartner(memberList);
    if (member == null) {
      LOGGER.debug("Send sendMembershipList() is called without action");
      sharedDataHistogram.update(System.currentTimeMillis() - startTime);
      return;
    }    
    for (Entry<String, SharedGossipDataMessage> innerEntry : gossipCore.getSharedData().entrySet()){
        UdpSharedGossipDataMessage message = new UdpSharedGossipDataMessage();
        message.setUuid(UUID.randomUUID().toString());
        message.setUriFrom(me.getId());
        message.setExpireAt(innerEntry.getValue().getExpireAt());
        message.setKey(innerEntry.getValue().getKey());
        message.setNodeId(innerEntry.getValue().getNodeId());
        message.setTimestamp(innerEntry.getValue().getTimestamp());
        message.setPayload(innerEntry.getValue().getPayload());
        gossipCore.sendOneWay(message, member.getUri());
    }
    sharedDataHistogram.update(System.currentTimeMillis() - startTime);
  }
  
  public void sendPerNodeData(LocalGossipMember me, List<LocalGossipMember> memberList){
    long startTime = System.currentTimeMillis();

    LocalGossipMember member = selectPartner(memberList);
    if (member == null) {
      LOGGER.debug("Send sendMembershipList() is called without action");
      sendPerNodeDataHistogram.update(System.currentTimeMillis() - startTime);
      return;
    }    
    for (Entry<String, ConcurrentHashMap<String, GossipDataMessage>> entry : gossipCore.getPerNodeData().entrySet()){
      for (Entry<String, GossipDataMessage> innerEntry : entry.getValue().entrySet()){
        UdpGossipDataMessage message = new UdpGossipDataMessage();
        message.setUuid(UUID.randomUUID().toString());
        message.setUriFrom(me.getId());
        message.setExpireAt(innerEntry.getValue().getExpireAt());
        message.setKey(innerEntry.getValue().getKey());
        message.setNodeId(innerEntry.getValue().getNodeId());
        message.setTimestamp(innerEntry.getValue().getTimestamp());
        message.setPayload(innerEntry.getValue().getPayload());
        gossipCore.sendOneWay(message, member.getUri());   
      }
    }
    sendPerNodeDataHistogram.update(System.currentTimeMillis() - startTime);
  }
  
  protected void sendToALiveMember(){
    LocalGossipMember member = selectPartner(gossipManager.getLiveMembers());
    System.out.println("send" );
    sendMembershipList(gossipManager.getMyself(), member);
  }
  
  protected void sendToDeadMember(){
    LocalGossipMember member = selectPartner(gossipManager.getDeadMembers());
    sendMembershipList(gossipManager.getMyself(), member);
  }
  
  /**
   * Performs the sending of the membership list, after we have incremented our own heartbeat.
   */
  protected void sendMembershipList(LocalGossipMember me, LocalGossipMember member) {
    long startTime = System.currentTimeMillis();
    me.setHeartbeat(System.nanoTime());
    if (member == null) {
      LOGGER.debug("Send sendMembershipList() is called without action");
      sendMembershipHistorgram.update(System.currentTimeMillis() - startTime);
      return;
    } else {
      LOGGER.debug("Send sendMembershipList() is called to " + member.toString());
    }
    UdpActiveGossipMessage message = new UdpActiveGossipMessage();
    message.setUriFrom(gossipManager.getMyself().getUri().toASCIIString());
    message.setUuid(UUID.randomUUID().toString());
    message.getMembers().add(convert(me));
    for (LocalGossipMember other : gossipManager.getMembers().keySet()) {
      message.getMembers().add(convert(other));
    }
    Response r = gossipCore.send(message, member.getUri());
    if (r instanceof ActiveGossipOk){
      //maybe count metrics here
    } else {
      LOGGER.debug("Message " + message + " generated response " + r);
    }
    sendMembershipHistorgram.update(System.currentTimeMillis() - startTime);
  }
  
  /**
   * 
   * @param memberList
   *          The list of members which are stored in the local list of members.
   * @return The chosen LocalGossipMember to gossip with.
   */
  protected LocalGossipMember selectPartner(List<LocalGossipMember> memberList) {
    LocalGossipMember member = null;
    if (memberList.size() > 0) {
      int randomNeighborIndex = random.nextInt(memberList.size());
      member = memberList.get(randomNeighborIndex);
    } else {
      LOGGER.debug("I am alone in this world.");
    }
    return member;
  }
  
  private GossipMember convert(LocalGossipMember member){
    GossipMember gm = new GossipMember();
    gm.setCluster(member.getClusterName());
    gm.setHeartbeat(member.getHeartbeat());
    gm.setUri(member.getUri().toASCIIString());
    gm.setId(member.getId());
    return gm;
  }
}
