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
package org.apache.gossip.manager.random;

import org.apache.gossip.GossipMember;
import org.apache.gossip.GossipSettings;
import org.apache.gossip.event.GossipListener;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.manager.impl.OnlyProcessReceivedPassiveGossipThread;

import java.util.ArrayList;
import java.util.List;

public class RandomGossipManager extends GossipManager {

  public static ManagerBuilder newBuilder() {
    return new ManagerBuilder();
  }

  public static final class ManagerBuilder {
    private String cluster;
    private String address;
    private int port;
    private String id;
    private GossipSettings settings;
    private List<GossipMember> gossipMembers;
    private GossipListener listener;

    private ManagerBuilder() {}

    private void checkArgument(boolean check, String msg) {
      if (!check) {
        throw new IllegalArgumentException(msg);
      }
    }

    public ManagerBuilder cluster(String cluster) {
      this.cluster = cluster;
      return this;
    }

    public ManagerBuilder address(String address) {
      this.address = address;
      return this;
    }

    public ManagerBuilder port(int port) {
      this.port = port;
      return this;
    }

    public ManagerBuilder withId(String id) {
      this.id = id;
      return this;
    }

    public ManagerBuilder settings(GossipSettings settings) {
      this.settings = settings;
      return this;
    }

    public ManagerBuilder gossipMembers(List<GossipMember> members) {
      this.gossipMembers = members == null ? new ArrayList<GossipMember>(): members;
      return this;
    }

    public ManagerBuilder listener(GossipListener listener) {
      this.listener = listener;
      return this;
    }

    public RandomGossipManager build() {
      checkArgument(id != null, "You must specify an id");
      checkArgument(cluster != null, "You must specify a cluster name");
      return new RandomGossipManager(cluster, address, port, id, settings, gossipMembers, listener);
    }
  }
  
  private RandomGossipManager(String cluster, String address, int port, String id,
                             GossipSettings settings, List<GossipMember> gossipMembers, GossipListener listener) {
    super(OnlyProcessReceivedPassiveGossipThread.class, RandomActiveGossipThread.class, cluster,
            address, port, id, settings, gossipMembers, listener);
  }
}
