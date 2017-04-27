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
import java.util.ArrayList;
import java.util.Collections;

import org.apache.gossip.LocalMember;

/**
 * Sends gossip traffic at different rates to other racks and data-centers.
 * This implementation controls the rate at which gossip traffic is shared. 
 * There are two constructs Datacenter and Rack. It is assumed that bandwidth and latency is higher
 * in the rack than in the the datacenter. We can adjust the rate at which we send messages to each group.
 * 
 */
public class DatacenterRackAwareGossipStrategy implements GossipStrategy {

  public static final String DATACENTER = "datacenter";
  public static final String RACK = "rack";
  
  private final ClusterModel clusterModel;
  private int sameRackGossipIntervalMs = 100;
  private int sameDcGossipIntervalMs = 500;
  private int differentDatacenterGossipIntervalMs = 1000;
  private int randomDeadMemberSendIntervalMs = 250;
  
  public DatacenterRackAwareGossipStrategy(ClusterModel clusterModel) {
    this.clusterModel = clusterModel;
    try {
      sameRackGossipIntervalMs = Integer.parseInt(clusterModel.getSettings()
              .getActiveGossipProperties().get("sameRackGossipIntervalMs"));
    } catch (RuntimeException ex) { }
    try {
      sameDcGossipIntervalMs = Integer.parseInt(clusterModel.getSettings()
              .getActiveGossipProperties().get("sameDcGossipIntervalMs"));
    } catch (RuntimeException ex) { }
    try {
      differentDatacenterGossipIntervalMs = Integer.parseInt(clusterModel.getSettings()
              .getActiveGossipProperties().get("differentDatacenterGossipIntervalMs"));
    } catch (RuntimeException ex) { }
    try {
      randomDeadMemberSendIntervalMs = Integer.parseInt(clusterModel.getSettings()
              .getActiveGossipProperties().get("randomDeadMemberSendIntervalMs"));
    } catch (RuntimeException ex) { }
  }

  /** setup gossip schedule */
  @Override
  public void install(ActiveGossiper gossiper) {
    //same rack
    gossiper.schedule(() -> sendToSameRackMember(gossiper), sameRackGossipIntervalMs);
    gossiper.schedule(() -> sendToSameRackMemberPerNode(gossiper), sameRackGossipIntervalMs);
    gossiper.schedule(() -> sendToSameRackShared(gossiper), sameRackGossipIntervalMs);
    
    //same dc different rack
    gossiper.schedule(() -> sameDcDiffernetRackMember(gossiper), sameDcGossipIntervalMs);
    gossiper.schedule(() -> sameDcDiffernetRackPerNode(gossiper), sameDcGossipIntervalMs);
    gossiper.schedule(() -> sameDcDiffernetRackShared(gossiper), sameDcGossipIntervalMs);
    
    //different dc
    gossiper.schedule(() -> differentDcMember(gossiper), differentDatacenterGossipIntervalMs);
    gossiper.schedule(() -> differentDcPerNode(gossiper), differentDatacenterGossipIntervalMs);
    gossiper.schedule(() -> differentDcShared(gossiper), differentDatacenterGossipIntervalMs);
    
    //the dead
    gossiper.schedule(() -> sendToDeadMember(gossiper), randomDeadMemberSendIntervalMs);
  }

  private void sendToDeadMember(ActiveGossiper gossiper) {
    gossiper.sendMembershipList(clusterModel.getMyself(), ActiveGossiper.selectPartner(clusterModel.getDeadMembers()));
  }
  
  private List<LocalMember> differentDataCenter(){
    String myDc = clusterModel.getMyself().getProperties().get(DATACENTER);
    String rack = clusterModel.getMyself().getProperties().get(RACK);
    if (myDc == null|| rack == null){
      return Collections.emptyList();
    }
    List<LocalMember> notMyDc = new ArrayList<LocalMember>(10);
    for (LocalMember i : clusterModel.getLiveMembers()){
      if (!myDc.equals(i.getProperties().get(DATACENTER))){
        notMyDc.add(i);
      }
    }
    return notMyDc;
  }
  
  private List<LocalMember> sameDatacenterDifferentRack(){
    String myDc = clusterModel.getMyself().getProperties().get(DATACENTER);
    String rack = clusterModel.getMyself().getProperties().get(RACK);
    if (myDc == null|| rack == null){
      return Collections.emptyList();
    }
    List<LocalMember> notMyDc = new ArrayList<LocalMember>(10);
    for (LocalMember i : clusterModel.getLiveMembers()){
      if (myDc.equals(i.getProperties().get(DATACENTER)) && !rack.equals(i.getProperties().get(RACK))){
        notMyDc.add(i);
      }
    }
    return notMyDc;
  }
    
  private List<LocalMember> sameRackNodes(){
    String myDc = clusterModel.getMyself().getProperties().get(DATACENTER);
    String rack = clusterModel.getMyself().getProperties().get(RACK);
    if (myDc == null|| rack == null){
      return Collections.emptyList();
    }
    List<LocalMember> sameDcAndRack = new ArrayList<LocalMember>(10);
    for (LocalMember i : clusterModel.getLiveMembers()){
      if (myDc.equals(i.getProperties().get(DATACENTER))
              && rack.equals(i.getProperties().get(RACK))){
        sameDcAndRack.add(i);
      }
    }
    return sameDcAndRack;
  }

  private void sendToSameRackMember(ActiveGossiper gossiper) {
    LocalMember i = ActiveGossiper.selectPartner(sameRackNodes());
    gossiper.sendMembershipList(clusterModel.getMyself(), i);
  }
  
  private void sendToSameRackMemberPerNode(ActiveGossiper gossiper) {
    gossiper.sendPerNodeData(clusterModel.getMyself(), ActiveGossiper.selectPartner(sameRackNodes()));
  }
  
  private void sendToSameRackShared(ActiveGossiper gossiper) {
    gossiper.sendSharedData(clusterModel.getMyself(), ActiveGossiper.selectPartner(sameRackNodes()));
  }
  
  private void differentDcMember(ActiveGossiper gossiper) {
    gossiper.sendMembershipList(clusterModel.getMyself(), ActiveGossiper.selectPartner(differentDataCenter()));
  }
  
  private void differentDcPerNode(ActiveGossiper gossiper) {
    gossiper.sendPerNodeData(clusterModel.getMyself(), ActiveGossiper.selectPartner(differentDataCenter()));
  }
  
  private void differentDcShared(ActiveGossiper gossiper) {
    gossiper.sendSharedData(clusterModel.getMyself(), ActiveGossiper.selectPartner(differentDataCenter()));
  }
  
  private void sameDcDiffernetRackMember(ActiveGossiper gossiper) {
    gossiper.sendMembershipList(clusterModel.getMyself(), ActiveGossiper.selectPartner(sameDatacenterDifferentRack()));
  }
  
  private void sameDcDiffernetRackPerNode(ActiveGossiper gossiper) {
    gossiper.sendPerNodeData(clusterModel.getMyself(), ActiveGossiper.selectPartner(sameDatacenterDifferentRack()));
  }
  
  private void sameDcDiffernetRackShared(ActiveGossiper gossiper) {
    gossiper.sendSharedData(clusterModel.getMyself(), ActiveGossiper.selectPartner(sameDatacenterDifferentRack()));
  }
}
