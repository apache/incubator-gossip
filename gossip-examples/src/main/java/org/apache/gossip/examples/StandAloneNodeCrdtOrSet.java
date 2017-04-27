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
package org.apache.gossip.examples;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Arrays;
import org.apache.gossip.GossipSettings;
import org.apache.gossip.Member;
import org.apache.gossip.crdt.GrowOnlyCounter;
import org.apache.gossip.crdt.OrSet;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.manager.GossipManagerBuilder;
import org.apache.gossip.model.SharedDataMessage;

public class StandAloneNodeCrdtOrSet {
  public static void main (String [] args) throws InterruptedException, IOException{
    GossipSettings s = new GossipSettings();
    s.setWindowSize(1000);
    s.setGossipInterval(100);
    GossipManager gossipService = GossipManagerBuilder.newBuilder()
            .cluster("mycluster")
            .uri(URI.create(args[0]))
            .id(args[1])
            .gossipMembers(Arrays.asList( new Member("mycluster", URI.create(args[2]), args[3])))
            .gossipSettings(s)
            .build();
    gossipService.init();
    
    new Thread(() -> {
      while (true){
      System.out.println("Live: " + gossipService.getLiveMembers());
      System.out.println("Dead: " + gossipService.getDeadMembers());
      System.out.println("---------- " + (gossipService.findCrdt("abc") == null ? "": 
          gossipService.findCrdt("abc").value()));
      System.out.println("********** " + gossipService.findCrdt("abc"));
      System.out.println("^^^^^^^^^^ " + (gossipService.findCrdt("def") == null ? "": 
        gossipService.findCrdt("def").value()));
      System.out.println("$$$$$$$$$$ " + gossipService.findCrdt("def"));
      try {
        Thread.sleep(2000);
      } catch (Exception e) {}
      }
    }).start();
    
    String line = null;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))){
      while ( (line = br.readLine()) != null){
        System.out.println(line);
        char op = line.charAt(0);
        String val = line.substring(2);
        if (op == 'a'){
          addData(val, gossipService);
        } else if (op == 'r') {
          removeData(val, gossipService);
        } else if (op == 'g'){
          gcount(val, gossipService);
        }
      }
    }
  }
  
  private static void gcount(String val, GossipManager gossipManager){
    GrowOnlyCounter c = (GrowOnlyCounter) gossipManager.findCrdt("def");
    Long l = Long.valueOf(val);
    if (c == null){
      c = new GrowOnlyCounter(new GrowOnlyCounter.Builder(gossipManager).increment((l)));
    } else {
      c = new GrowOnlyCounter(c, new GrowOnlyCounter.Builder(gossipManager).increment((l)));
    }
    SharedDataMessage m = new SharedDataMessage();
    m.setExpireAt(Long.MAX_VALUE);
    m.setKey("def");
    m.setPayload(c);
    m.setTimestamp(System.currentTimeMillis());
    gossipManager.merge(m);
  }
  
  private static void removeData(String val, GossipManager gossipService){
    @SuppressWarnings("unchecked")
    OrSet<String> s = (OrSet<String>) gossipService.findCrdt("abc");
    SharedDataMessage m = new SharedDataMessage();
    m.setExpireAt(Long.MAX_VALUE);
    m.setKey("abc");
    m.setPayload(new OrSet<String>(s , new OrSet.Builder<String>().remove(val)));
    m.setTimestamp(System.currentTimeMillis());
    gossipService.merge(m);
  }
  
  private static void addData(String val, GossipManager gossipService){
    SharedDataMessage m = new SharedDataMessage();
    m.setExpireAt(Long.MAX_VALUE);
    m.setKey("abc");
    m.setPayload(new OrSet<String>(val));
    m.setTimestamp(System.currentTimeMillis());
    gossipService.merge(m);
  }
}
