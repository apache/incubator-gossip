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
import java.util.List;

import org.apache.gossip.GossipSettings;
import org.apache.gossip.LocalMember;
import org.apache.gossip.RemoteMember;
import org.apache.gossip.crdt.PNCounter;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.manager.GossipManagerBuilder;
import org.apache.gossip.model.SharedDataMessage;

public class StandAlonePNCounter {
  private static ExampleCommon common = new ExampleCommon();
  private static String lastInput = "{None}";

  public static void main(String[] args) throws InterruptedException, IOException {
    args = common.checkArgsForClearFlag(args);
    GossipSettings s = new GossipSettings();
    s.setWindowSize(1000);
    s.setGossipInterval(100);
    GossipManager gossipService = GossipManagerBuilder
            .newBuilder()
            .cluster("mycluster")
            .uri(URI.create(args[0])).id(args[1])
            .gossipMembers(
                    Arrays.asList(new RemoteMember("mycluster", URI.create(args[2]), args[3])))
            .gossipSettings(s)
            .build();
    gossipService.init();

    new Thread(() -> {
      while (true) {
        common.optionallyClearTerminal();
        printLiveMembers(gossipService);
        printDeadMambers(gossipService);
        printValues(gossipService);
        try {
          Thread.sleep(2000);
        } catch (Exception ignore) {
        }
      }
    }).start();

    String line = null;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
      while ((line = br.readLine()) != null) {
        System.out.println(line);
        char op = line.charAt(0);
        char blank = line.charAt(1);
        String val = line.substring(2);
        Long l = null;
        boolean valid = true;
        try {
           l = Long.valueOf(val);
        } catch (NumberFormatException ex) {
          valid = false;
        }
        valid = valid &&
          (
            (blank == ' ') &&
            ((op == 'i') || (op == 'd'))
          );
        if (valid) {
          if (op == 'i') {
            increment(l, gossipService);
          } else if (op == 'd') {
            decrement(l, gossipService);
          }
        }
        setLastInput(line,valid);
      }
    }
  }

  private static void printValues(GossipManager gossipService) {
    System.out.println("Last Input: " + getLastInput());
    System.out.println("---------- " + (gossipService.findCrdt("myPNCounter") == null ? ""
            : gossipService.findCrdt("myPNCounter").value()));
    System.out.println("********** " + gossipService.findCrdt("myPNCounter"));
  }

  private static void printDeadMambers(GossipManager gossipService) {
    List<LocalMember> members = gossipService.getDeadMembers();
    if (members.isEmpty()) {
       System.out.println("Dead: (none)");
       return;
    }
    System.out.println("Dead: " + members.get(0));
    for (int i = 1; i < members.size(); i++) {
      System.out.println("    : " + members.get(i)); 
    }
  }

  private static void printLiveMembers(GossipManager gossipService) {
    List<LocalMember> members = gossipService.getLiveMembers();
    if (members.isEmpty()) {
       System.out.println("Live: (none)");
       return;
    }
    System.out.println("Live: " + members.get(0));
    for (int i = 1; i < members.size(); i++) {
      System.out.println("    : " + members.get(i)); 
    }
  }

  private static void increment(Long l, GossipManager gossipManager) {
    PNCounter c = (PNCounter) gossipManager.findCrdt("myPNCounter");
    if (c == null) {
      c = new PNCounter(new PNCounter.Builder(gossipManager).increment((l)));
    } else {
      c = new PNCounter(c, new PNCounter.Builder(gossipManager).increment((l)));
    }
    SharedDataMessage m = new SharedDataMessage();
    m.setExpireAt(Long.MAX_VALUE);
    m.setKey("myPNCounter");
    m.setPayload(c);
    m.setTimestamp(System.currentTimeMillis());
    gossipManager.merge(m);
  }

  private static void decrement(Long l, GossipManager gossipManager) {
    PNCounter c = (PNCounter) gossipManager.findCrdt("myPNCounter");
    if (c == null) {
      c = new PNCounter(new PNCounter.Builder(gossipManager).decrement((l)));
    } else {
      c = new PNCounter(c, new PNCounter.Builder(gossipManager).decrement((l)));
    }
    SharedDataMessage m = new SharedDataMessage();
    m.setExpireAt(Long.MAX_VALUE);
    m.setKey("myPNCounter");
    m.setPayload(c);
    m.setTimestamp(System.currentTimeMillis());
    gossipManager.merge(m);
  }
  
  private static void setLastInput(String input, boolean valid) {
    lastInput = input;
    if (! valid) {
      lastInput += " (invalid)";
    }
  }

  private static String getLastInput() {
    return lastInput;
  }

}
