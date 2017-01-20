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

import com.codahale.metrics.MetricRegistry;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.apache.gossip.GossipService;
import org.apache.gossip.GossipSettings;
import org.apache.gossip.RemoteGossipMember;

public class StandAloneNode {
  public static void main (String [] args) throws UnknownHostException, InterruptedException{
    GossipSettings s = new GossipSettings();
    s.setWindowSize(10);
    s.setConvictThreshold(1.0);
    s.setGossipInterval(1000);
    GossipService gossipService = new GossipService("mycluster",  URI.create(args[0]), args[1],
            Arrays.asList( new RemoteGossipMember("mycluster", URI.create(args[2]), args[3])), s, (a,b) -> {}, new MetricRegistry());
    gossipService.start();
    while (true){
      System.out.println( "Live: " + gossipService.getGossipManager().getLiveMembers());
      System.out.println( "Dead: " + gossipService.getGossipManager().getDeadMembers());
      Thread.sleep(2000);
    }
  }
}
