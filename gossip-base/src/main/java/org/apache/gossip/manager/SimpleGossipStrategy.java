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

import org.apache.gossip.LocalMember;

/**
 * Base implementation gossips randomly to live nodes periodically gossips to dead ones
 *
 */
public class SimpleGossipStrategy implements GossipStrategy {

  private final ClusterModel clusterModel;
  
  public SimpleGossipStrategy(ClusterModel clusterModel) {
    this.clusterModel = clusterModel;
  }

  @Override
  public void install(ActiveGossiper gossiper) {
    gossiper.schedule(() -> sendToALiveMember(gossiper), clusterModel.getSettings().getGossipInterval());
    gossiper.schedule(() -> sendToDeadMember(gossiper), clusterModel.getSettings().getGossipInterval());
    gossiper.schedule(
        () -> gossiper.sendPerNodeData(clusterModel.getMyself(), ActiveGossiper.selectPartner(clusterModel.getLiveMembers())),
        clusterModel.getSettings().getGossipInterval());
    gossiper.schedule(
        () -> gossiper.sendSharedData(clusterModel.getMyself(), ActiveGossiper.selectPartner(clusterModel.getLiveMembers())),
        clusterModel.getSettings().getGossipInterval());
  }

  protected void sendToALiveMember(ActiveGossiper gossiper){
    LocalMember member = ActiveGossiper.selectPartner(clusterModel.getLiveMembers());
    gossiper.sendMembershipList(clusterModel.getMyself(), member);
  }
  
  protected void sendToDeadMember(ActiveGossiper gossiper){
    LocalMember member = ActiveGossiper.selectPartner(clusterModel.getDeadMembers());
    gossiper.sendMembershipList(clusterModel.getMyself(), member);
  }
}
