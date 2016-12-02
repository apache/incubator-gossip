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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.management.Notification;
import javax.management.NotificationListener;

import org.apache.log4j.Logger;

import org.apache.gossip.GossipMember;
import org.apache.gossip.GossipService;
import org.apache.gossip.GossipSettings;
import org.apache.gossip.LocalGossipMember;
import org.apache.gossip.event.GossipListener;
import org.apache.gossip.event.GossipState;
import org.apache.gossip.manager.impl.OnlyProcessReceivedPassiveGossipThread;

import org.apache.gossip.model.GossipDataMessage;
import org.apache.gossip.model.SharedGossipDataMessage;


public abstract class GossipManager implements NotificationListener {

  public static final Logger LOGGER = Logger.getLogger(GossipManager.class);

  public static final int MAX_PACKET_SIZE = 102400;

  private final ConcurrentSkipListMap<LocalGossipMember, GossipState> members;

  private final LocalGossipMember me;

  private final GossipSettings settings;

  private final AtomicBoolean gossipServiceRunning;

  private final GossipListener listener;

  private ActiveGossipThread activeGossipThread;

  private PassiveGossipThread passiveGossipThread;

  private ExecutorService gossipThreadExecutor;
  
  private final GossipCore gossipCore;
  
  private final DataReaper dataReaper;
  
  private final Clock clock;

  public GossipManager(String cluster,
          URI uri, String id, GossipSettings settings,
          List<GossipMember> gossipMembers, GossipListener listener) {
    
    this.settings = settings;
    gossipCore = new GossipCore(this);
    clock = new SystemClock();
    dataReaper = new DataReaper(gossipCore, clock);
    me = new LocalGossipMember(cluster, uri, id, System.currentTimeMillis(), this,
            settings.getCleanupInterval());
    members = new ConcurrentSkipListMap<>();
    for (GossipMember startupMember : gossipMembers) {
      if (!startupMember.equals(me)) {
        LocalGossipMember member = new LocalGossipMember(startupMember.getClusterName(),
                startupMember.getUri(), startupMember.getId(),
                System.currentTimeMillis(), this, settings.getCleanupInterval());
        members.put(member, GossipState.UP);
        GossipService.LOGGER.debug(member);
      }
    }
    gossipThreadExecutor = Executors.newCachedThreadPool();
    gossipServiceRunning = new AtomicBoolean(true);
    this.listener = listener;
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      public void run() {
        GossipService.LOGGER.debug("Service has been shutdown...");
      }
    }));
  }

  /**
   * All timers associated with a member will trigger this method when it goes off. The timer will
   * go off if we have not heard from this member in <code> _settings.T_CLEANUP </code> time.
   */
  @Override
  public void handleNotification(Notification notification, Object handback) {
    LocalGossipMember deadMember = (LocalGossipMember) notification.getUserData();
    GossipService.LOGGER.debug("Dead member detected: " + deadMember);
    members.put(deadMember, GossipState.DOWN);
    if (listener != null) {
      listener.gossipEvent(deadMember, GossipState.DOWN);
    }
  }

  public void reviveMember(LocalGossipMember m) {
    for (Entry<LocalGossipMember, GossipState> it : this.members.entrySet()) {
      if (it.getKey().getId().equals(m.getId())) {
        it.getKey().disableTimer();
      }
    }
    members.remove(m);
    members.put(m, GossipState.UP);
    if (listener != null) {
      listener.gossipEvent(m, GossipState.UP);
    }
  }

  public void createOrReviveMember(LocalGossipMember m) {
    members.put(m, GossipState.UP);
    if (listener != null) {
      listener.gossipEvent(m, GossipState.UP);
    }
  }

  public GossipSettings getSettings() {
    return settings;
  }

  // TODO: Use some java 8 goodness for these functions.
  
  /**
   * @return a read only list of members found in the DOWN state.
   */
  public List<LocalGossipMember> getDeadMembers() {
    return Collections.unmodifiableList(
            members.entrySet().stream()
            .filter(entry -> GossipState.DOWN.equals(entry.getValue()))
            .map(Entry::getKey)
            .collect(Collectors.toList()));
  }

  /**
   * 
   * @return a read only list of members found in the UP state
   */
  public List<LocalGossipMember> getLiveMembers() {
    return Collections.unmodifiableList(members.entrySet().stream()
            .filter(entry -> GossipState.UP.equals(entry.getValue()))
            .map(Entry::getKey)
            .collect(Collectors.toList()));
  }

  public LocalGossipMember getMyself() {
    return me;
  }

  /**
   * Starts the client. Specifically, start the various cycles for this protocol. Start the gossip
   * thread and start the receiver thread.
   */
  public void init() {
    for (LocalGossipMember member : members.keySet()) {
      if (member != me) {
        member.startTimeoutTimer();
      }
    }
    passiveGossipThread = new OnlyProcessReceivedPassiveGossipThread(this, gossipCore);
    gossipThreadExecutor.execute(passiveGossipThread);
    activeGossipThread = new ActiveGossipThread(this, this.gossipCore);
    activeGossipThread.init();
    dataReaper.init();
    GossipService.LOGGER.debug("The GossipService is started.");
  }

  /**
   * Shutdown the gossip service.
   */
  public void shutdown() {
    gossipServiceRunning.set(false);
    gossipThreadExecutor.shutdown();
    gossipCore.shutdown();
    dataReaper.close();
    if (passiveGossipThread != null) {
      passiveGossipThread.shutdown();
    }
    if (activeGossipThread != null) {
      activeGossipThread.shutdown();
    }
    try {
      boolean result = gossipThreadExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS);
      if (!result) {
        LOGGER.error("executor shutdown timed out");
      }
    } catch (InterruptedException e) {
      LOGGER.error(e);
    }
  }
  
  public void gossipPerNodeData(GossipDataMessage message){
    Objects.nonNull(message.getKey());
    Objects.nonNull(message.getTimestamp());
    Objects.nonNull(message.getPayload());
    message.setNodeId(me.getId());
    gossipCore.addPerNodeData(message);
  }
  
  public void gossipSharedData(SharedGossipDataMessage message){
    Objects.nonNull(message.getKey());
    Objects.nonNull(message.getTimestamp());
    Objects.nonNull(message.getPayload());
    message.setNodeId(me.getId());
    gossipCore.addSharedData(message);
  }
  
  public GossipDataMessage findPerNodeGossipData(String nodeId, String key){
    ConcurrentHashMap<String, GossipDataMessage> j = gossipCore.getPerNodeData().get(nodeId);
    if (j == null){
      return null;
    } else {
      GossipDataMessage l = j.get(key);
      if (l == null){
        return null;
      }
      if (l.getExpireAt() != null && l.getExpireAt() < clock.currentTimeMillis()) {
        return null;
      }
      return l;
    }
  }
  
  public SharedGossipDataMessage findSharedGossipData(String key){
    SharedGossipDataMessage l = gossipCore.getSharedData().get(key);
    if (l == null){
      return null;
    }
    if (l.getExpireAt() < clock.currentTimeMillis()){
      return null;
    } else {
      return l;
    }
  }

  public DataReaper getDataReaper() {
    return dataReaper;
  }
            
}
