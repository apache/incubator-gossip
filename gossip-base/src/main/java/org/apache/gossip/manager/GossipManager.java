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

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.gossip.GossipSettings;
import org.apache.gossip.LocalMember;
import org.apache.gossip.Member;
import org.apache.gossip.crdt.Crdt;
import org.apache.gossip.event.GossipListener;
import org.apache.gossip.event.GossipState;
import org.apache.gossip.manager.handlers.CrdtManager;
import org.apache.gossip.manager.handlers.MessageHandler;
import org.apache.gossip.model.Base;
import org.apache.gossip.model.PerNodeDataMessage;
import org.apache.gossip.model.SharedDataMessage;
import org.apache.gossip.protocol.ProtocolManager;
import org.apache.gossip.transport.BytesListener;
import org.apache.gossip.transport.TransportManager;
import org.apache.gossip.utils.ReflectionUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class GossipManager implements ClusterModel, CrdtManager {

  public static final Logger LOGGER = Logger.getLogger(GossipManager.class);
  
  // this mapper is used for ring and user-data persistence only. NOT messages.
  public static final ObjectMapper metdataObjectMapper = new ObjectMapper() {{
    enableDefaultTyping();
    configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, false);
  }};

  private final ConcurrentSkipListMap<LocalMember, GossipState> members;
  private final LocalMember me;
  private final GossipSettings settings;
  private final AtomicBoolean gossipServiceRunning;
  
  private final MessagingManager messaging;
  
  private final ActiveGossiper activeGossiper;
  
  private final GossipCore gossipCore;
  private final DataReaper dataReaper;
  private final Clock clock;
  private final ScheduledExecutorService scheduledServiced;
  private final MetricRegistry registry;
  private final RingStatePersister ringState;
  private final UserDataPersister userDataState;
  private final GossipMemberStateRefresher memberStateRefresher;
  
  private final MessageHandler messageHandler;
  
  public GossipManager(String cluster,
                       URI uri, String id, Map<String, String> properties, GossipSettings settings,
                       List<Member> gossipMembers, GossipListener listener, MetricRegistry registry,
                       MessageHandler messageHandler) {
    this.settings = settings;
    this.messageHandler = messageHandler;

    clock = new SystemClock();
    me = new LocalMember(cluster, uri, id, clock.nanoTime(), properties,
            settings.getWindowSize(), settings.getMinimumSamples(), settings.getDistribution());
    gossipCore = new GossipCore(this, registry);
    dataReaper = new DataReaper(gossipCore, clock);
    members = new ConcurrentSkipListMap<>();
    for (Member startupMember : gossipMembers) {
      if (!startupMember.equals(me)) {
        LocalMember member = new LocalMember(startupMember.getClusterName(),
                startupMember.getUri(), startupMember.getId(),
                clock.nanoTime(), startupMember.getProperties(), settings.getWindowSize(),
                settings.getMinimumSamples(), settings.getDistribution());
        //TODO should members start in down state?
        members.put(member, GossipState.DOWN);
      }
    }
    gossipServiceRunning = new AtomicBoolean(true);
    this.scheduledServiced = Executors.newScheduledThreadPool(1);
    this.registry = registry;
    this.ringState = new RingStatePersister(GossipManager.buildRingStatePath(this), this);
    this.userDataState = new UserDataPersister(
        gossipCore,
        GossipManager.buildPerNodeDataPath(this),
        GossipManager.buildSharedDataPath(this));
    this.memberStateRefresher = new GossipMemberStateRefresher(members, settings, listener, gossipCore::findPerNodeGossipData);
    readSavedRingState();
    readSavedDataState();
    
    // protocol manager and transport managers are specified in settings.
    // construct them here via reflection.
    final ProtocolManager protocolManager = ReflectionUtils.constructWithReflection(
        settings.getProtocolManagerClass(),
        new Class<?>[] { GossipSettings.class, String.class, MetricRegistry.class },
        new Object[] { settings, me.getId(), this.getRegistry() }
    );
    
    TransportManager transportManager = ReflectionUtils.constructWithReflection(
        settings.getTransportManagerClass(),
        new Class<?>[] { ClusterModel.class },
        new Object[] { this }
    );
    
    transportManager.addBytesListener(new BytesListener() {
      @Override
      public void bytesReceived(byte[] buf) throws IOException {
        Base message = protocolManager.read(buf);
        if (!messageHandler.invoke(GossipManager.this, message)) {
          LOGGER.warn("received message can not be handled");
        }
        getMemberStateRefresher().run();
      }
    });
    this.messaging = new MessagingManager(transportManager, protocolManager, clock, registry);
    this.activeGossiper = new ActiveGossiper(this, messaging, registry);
  }

  public MessageHandler getMessageHandler() {
    return messageHandler;
  }

  public Map<LocalMember, GossipState> getMembers() {
    return members;
  }

  public GossipSettings getSettings() {
    return settings;
  }

  @Override
  public Collection<SharedDataMessage> getSharedData() {
    return gossipCore.getSharedData();
  }

  @Override
  public Collection<PerNodeDataMessage> getPerNodeData() {
    return gossipCore.getPerNodeData();
  }

  /**
   * @return a read only list of members found in the DOWN state.
   */
  public List<LocalMember> getDeadMembers() {
    return Collections.unmodifiableList(
            members.entrySet()
                    .stream()
                    .filter(entry -> GossipState.DOWN.equals(entry.getValue()))
                    .map(Entry::getKey).collect(Collectors.toList()));
  }

  /**
   *
   * @return a read only list of members found in the UP state
   */
  public List<LocalMember> getLiveMembers() {
    return Collections.unmodifiableList(
            members.entrySet()
                    .stream()
                    .filter(entry -> GossipState.UP.equals(entry.getValue()))
                    .map(Entry::getKey).collect(Collectors.toList()));
  }

  public LocalMember getMyself() {
    return me;
  }

  @Override
  public long nanoTime() {
    return clock.nanoTime();
  }

  @Override
  public long currentTimeMillis() {
    return clock.currentTimeMillis();
  }

  /**
   * Starts the client. Specifically, start the various cycles for this protocol. Start the gossip
   * thread and start the receiver thread.
   */
  public void init() {
    // start processing gossip messages.
    messaging.start();
    
    // construct the gossip schedule using reflection.
    GossipStrategy activeGossipStrategy = ReflectionUtils.constructWithReflection(
      getSettings().getGossipStrategy(),
        new Class<?>[]{ ClusterModel.class },
        new Object[]{ GossipManager.this }
    );
    activeGossipStrategy.install(activeGossiper);
    
    
    dataReaper.init();
    if (settings.isPersistRingState()) {
      scheduledServiced.scheduleAtFixedRate(ringState, 60, 60, TimeUnit.SECONDS);
    }
    if (settings.isPersistDataState()) {
      scheduledServiced.scheduleAtFixedRate(userDataState, 60, 60, TimeUnit.SECONDS);
    }
    scheduledServiced.scheduleAtFixedRate(memberStateRefresher, 0, 100, TimeUnit.MILLISECONDS);
    LOGGER.debug("The GossipManager is started.");
  }
  
  private void readSavedRingState() {
    if (settings.isPersistRingState()) {
      for (LocalMember l : ringState.readFromDisk()) {
        LocalMember member = new LocalMember(l.getClusterName(),
            l.getUri(), l.getId(),
            clock.nanoTime(), l.getProperties(), settings.getWindowSize(),
            settings.getMinimumSamples(), settings.getDistribution());
        members.putIfAbsent(member, GossipState.DOWN);
      }
    }
  }

  private void readSavedDataState() {
    if (settings.isPersistDataState()) {
      userDataState.readPerNodeFromDisk().forEach(gossipCore::addPerNodeData);
    }
    if (settings.isPersistRingState()) {
      userDataState.readSharedDataFromDisk().forEach(gossipCore::addSharedData);
    }
  }

  /**
   * Shutdown the gossip service.
   */
  public void shutdown() {
    gossipServiceRunning.set(false);
    activeGossiper.shutdown();
    messaging.shutdown();
    dataReaper.close();
    scheduledServiced.shutdown();
    try {
      scheduledServiced.awaitTermination(1, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOGGER.error(e);
    }
    scheduledServiced.shutdownNow();
  }

  public void gossipPerNodeData(PerNodeDataMessage message){
    Objects.nonNull(message.getKey());
    Objects.nonNull(message.getTimestamp());
    Objects.nonNull(message.getPayload());
    message.setNodeId(me.getId());
    gossipCore.addPerNodeData(message);
  }

  public void gossipSharedData(SharedDataMessage message){
    Objects.nonNull(message.getKey());
    Objects.nonNull(message.getTimestamp());
    Objects.nonNull(message.getPayload());
    message.setNodeId(me.getId());
    gossipCore.addSharedData(message);
  }

  public RingStatePersister getRingState() {
    return ringState;
  }

  public UserDataPersister getUserDataState() {
    return userDataState;
  }

  public GossipMemberStateRefresher getMemberStateRefresher() {
    return memberStateRefresher;
  }

  public MetricRegistry getRegistry() {
    return registry;
  }
  
  public MessagingManager getMessaging() { return messaging; }
  
  public GossipCore getState() { return gossipCore; }
  
  //
  // CRDT methods
  //

  @Override
  public Crdt findCrdt(String key) {
    return gossipCore.findCrdt(key, clock.currentTimeMillis());
  }

  @Override
  public Crdt merge(SharedDataMessage message) {
    return gossipCore.merge(message, me.getId());
  }

  @Override
  public PerNodeDataMessage findPerNodeGossipData(String nodeId, String key) {
    return gossipCore.findPerNodeGossipData(nodeId, key);
  }

  @Override
  public SharedDataMessage findSharedGossipData(String key) {
    return gossipCore.findSharedGossipData(key);
  }

  // todo: consider making these path methods part of GossipSettings
  
  public static File buildRingStatePath(GossipManager manager) {
    return new File(manager.getSettings().getPathToRingState(), "ringstate." + manager.getMyself().getClusterName() + "."
        + manager.getMyself().getId() + ".json");
  }
  
  public static File buildSharedDataPath(GossipManager manager){
    return new File(manager.getSettings().getPathToDataState(), "shareddata."
            + manager.getMyself().getClusterName() + "." + manager.getMyself().getId() + ".json");
  }
  
  public static File buildPerNodeDataPath(GossipManager manager) {
    return new File(manager.getSettings().getPathToDataState(), "pernodedata."
            + manager.getMyself().getClusterName() + "." + manager.getMyself().getId() + ".json");
  }
}
