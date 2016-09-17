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
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;

import org.apache.gossip.event.GossipListener;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.manager.random.RandomGossipManager;
import org.apache.log4j.Logger;

/**
 * This object represents the service which is responsible for gossiping with other gossip members.
 * 
 * @author joshclemm, harmenw
 */
public class GossipService {

  public static final Logger LOGGER = Logger.getLogger(GossipService.class);

  private GossipManager gossipManager;
  
  protected final ExecutorService thread = Executors.newSingleThreadExecutor();

  /**
   * Constructor with the default settings.
   * 
   * @throws InterruptedException
   * @throws UnknownHostException
   */
  public GossipService(StartupSettings startupSettings) throws InterruptedException,
          UnknownHostException {
    this(startupSettings.getCluster(), startupSettings.getUri()
            , startupSettings.getId(), startupSettings.getGossipMembers(),
            startupSettings.getGossipSettings(), null);
  }

  /**
   * Setup the client's lists, gossiping parameters, and parse the startup config file.
   * 
   * @throws InterruptedException
   * @throws UnknownHostException
   */
  public GossipService(String cluster, URI uri, String id,
          List<GossipMember> gossipMembers, GossipSettings settings, GossipListener listener)
          throws InterruptedException, UnknownHostException {
    gossipManager = RandomGossipManager.newBuilder()
        .withId(id)
        .cluster(cluster)
        .uri(uri)
        .settings(settings)
        .gossipMembers(gossipMembers)
        .listener(listener)
        .build();
  }

  public void start() {
    LOGGER.debug("Starting: " + gossipManager.getName() + " - " + get_gossipManager().getMyself().getUri());
    thread.submit(gossipManager);
  }

  public void shutdown() {
    gossipManager.shutdown();
    thread.shutdown();	
    try {
        boolean result = thread.awaitTermination(1000, TimeUnit.MILLISECONDS);
        if (!result) {
          LOGGER.error("GossipService shutdown timed out!");
        }
      } catch (InterruptedException e) {
        LOGGER.error(e);
      }
  }

  public GossipManager get_gossipManager() {
    return gossipManager;
  }

  public void set_gossipManager(GossipManager _gossipManager) {
    this.gossipManager = _gossipManager;
  }

}
