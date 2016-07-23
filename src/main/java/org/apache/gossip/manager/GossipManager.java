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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.apache.gossip.manager.random.RandomActiveGossipThread;

public abstract class GossipManager extends Thread implements  NotificationListener {

  public static final Logger LOGGER = Logger.getLogger(GossipManager.class);

  public static final int MAX_PACKET_SIZE = 102400;

  private final ConcurrentSkipListMap<LocalGossipMember, GossipState> members;

  private final LocalGossipMember me;

  private final GossipSettings settings;

  private final AtomicBoolean gossipServiceRunning;

  private final GossipListener listener;
  
  protected final ServiceFactory serviceFactory;

  protected ServiceBundle bundle;

  private ExecutorService gossipThreadExecutor;
  
  private GossipCore gossipCore;

  public GossipManager(String cluster, URI uri, String id, 
		               GossipSettings settings, 
		               List<GossipMember> gossipMembers, 
		               GossipListener listener) {
    this.settings = settings;
    this.gossipCore = new GossipCore(this);
    this.serviceFactory = new ServiceFactory(this, gossipCore);
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
    List<LocalGossipMember> down = new ArrayList<>();
    for (Entry<LocalGossipMember, GossipState> entry : members.entrySet()) {
      if (GossipState.DOWN.equals(entry.getValue())) {
        down.add(entry.getKey());
      }
    }
    return Collections.unmodifiableList(down);
  }

  /**
   * 
   * @return a read only list of members found in the UP state
   */
  public List<LocalGossipMember> getLiveMembers() {
    List<LocalGossipMember> up = new ArrayList<>();
    for (Entry<LocalGossipMember, GossipState> entry : members.entrySet()) {
      if (GossipState.UP.equals(entry.getValue())) {
        up.add(entry.getKey());
      }
    }
    return Collections.unmodifiableList(up);
  }

  public LocalGossipMember getMyself() {
    return me;
  }

  public List<LocalGossipMember> getDeadList() {
    List<LocalGossipMember> up = new ArrayList<>();
    for (Entry<LocalGossipMember, GossipState> entry : members.entrySet()) {
      if (GossipState.DOWN.equals(entry.getValue())) {
        up.add(entry.getKey());
      }
    }
    return Collections.unmodifiableList(up);
  }

  /**
   * Starts the client. Specifically, start the various cycles for this protocol. Start the gossip
   * thread and start the receiver thread.
   */
  @Override 
  public void run() {
    for (LocalGossipMember member : members.keySet()) {
      if (member != me) {
        member.startTimeoutTimer();
      }
    }
    final List<Class<? extends Runnable>> services = new ArrayList<>();
    services.add(OnlyProcessReceivedPassiveGossipThread.class);
    services.add(RandomActiveGossipThread.class);
    this.bundle = serviceFactory.newServices(services);
    for(Runnable service: this.bundle.services()) {
      gossipThreadExecutor.submit(service);
    }
    try {
      this.bundle.guard().await();
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();	
    }
  }

  /**
   * Shutdown the gossip service.
   */
  public void shutdown() {
    gossipServiceRunning.set(false);
    gossipThreadExecutor.shutdown();
    gossipCore.shutdown();
    if(null != this.bundle) {
      for(Runnable runnable: this.bundle.services()) {
    	if(runnable instanceof Shutdownable) {
    	  ((Shutdownable) runnable).shutdown();
    	}
      }
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
}
class ServiceFactory {
	
  final Logger LOGGER = Logger.getLogger(ServiceFactory.class);

  final GossipManager manager;
  final GossipCore core;

  ServiceFactory(GossipManager manager, GossipCore core) {
    this.manager = manager;
    this.core = core;
  }
  
  ServiceBundle newServices(List<Class<? extends Runnable>> classes) {
	final CountDownLatch gate = new CountDownLatch(classes.size());
	final List<Runnable> services = new ArrayList<>();
	try {
	  for(Class<? extends Runnable> klz: classes) {
        final Constructor<? extends Runnable> constructor = 
           klz.getDeclaredConstructor(GossipManager.class, GossipCore.class, 
        		                      CountDownLatch.class);
        final Runnable thread = constructor.newInstance(manager, core, gate);
        services.add(thread);
	  }
    } catch (IllegalAccessException iae) {
      LOGGER.error("Can't access to the definition of classes ...", iae);
    } catch (InstantiationException ie) {
      LOGGER.error("Can't instantiate classes ...", ie);
    } catch (InvocationTargetException ite) {
      LOGGER.error("Can't invoke target exception ...", ite);
    } catch (NoSuchMethodException nsme) {
      LOGGER.error("Method not found ...", nsme);
    }
	return new ServiceBundle(services, gate);
  }
  
}

class ServiceBundle {
	
  final List<Runnable> runnables;
  final CountDownLatch gate;

  ServiceBundle(List<Runnable> services, CountDownLatch gate) {
	this.runnables = services;  
	this.gate = gate;
  }
  
  List<Runnable> services() {
	return Collections.unmodifiableList(runnables);
  }
  
  CountDownLatch guard() {
	return gate;  
  }

}
