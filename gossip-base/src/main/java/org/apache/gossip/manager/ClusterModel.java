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

import org.apache.gossip.GossipSettings;
import org.apache.gossip.LocalMember;
import org.apache.gossip.event.GossipState;
import org.apache.gossip.model.PerNodeDataMessage;
import org.apache.gossip.model.SharedDataMessage;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** The main intent of this class is to limit the exposure of its primary implemenation (GossipManager). */
public interface ClusterModel {
  Map<LocalMember, GossipState> getMembers();
  List<LocalMember> getLiveMembers();
  List<LocalMember> getDeadMembers();
  Collection<SharedDataMessage> getSharedData();      // ActiveGossiper
  Collection<PerNodeDataMessage> getPerNodeData();    // ActiveGossiper
  LocalMember getMyself();
  GossipSettings getSettings();
  long nanoTime();
  long currentTimeMillis();
}
