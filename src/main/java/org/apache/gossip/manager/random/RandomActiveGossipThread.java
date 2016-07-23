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

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.List;
import java.util.Random;
import java.util.UUID; 
import java.util.concurrent.CountDownLatch;
import org.apache.gossip.GossipService;
import org.apache.gossip.LocalGossipMember;
import org.apache.gossip.manager.ActiveGossipThread;
import org.apache.gossip.manager.GossipCore;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.model.ActiveGossipOk;
import org.apache.gossip.model.GossipMember;
import org.apache.gossip.model.Response;
import org.apache.gossip.udp.UdpActiveGossipMessage;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

public class RandomActiveGossipThread extends ActiveGossipThread {

  public static final Logger LOGGER = Logger.getLogger(RandomActiveGossipThread.class);
  protected ObjectMapper MAPPER = new ObjectMapper();
  
  /** The Random used for choosing a member to gossip with. */
  private final Random random;
  private final GossipCore gossipCore;

  public RandomActiveGossipThread(GossipManager gossipManager, 
		                          GossipCore gossipCore, 
		                          CountDownLatch gate) {
    super(gossipManager, gate);
    random = new Random();
    this.gossipCore = gossipCore;
  }

  /**
   * [The selectToSend() function.] Find a random peer from the local membership list. In the case
   * where this client is the only member in the list, this method will return null.
   * 
   * @return Member random member if list is greater than 1, null otherwise
   */
  protected LocalGossipMember selectPartner(List<LocalGossipMember> memberList) {
    LocalGossipMember member = null;
    if (memberList.size() > 0) {
      int randomNeighborIndex = random.nextInt(memberList.size());
      member = memberList.get(randomNeighborIndex);
    } else {
      GossipService.LOGGER.debug("I am alone in this world.");
    }
    return member;
  }

  protected void sendMembershipList(LocalGossipMember me, List<LocalGossipMember> memberList) {
    GossipService.LOGGER.debug("Send sendMembershipList() is called.");
    me.setHeartbeat(System.currentTimeMillis());
    LocalGossipMember member = selectPartner(memberList);
    if (member == null) {
      return;
    }
    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setSoTimeout(gossipManager.getSettings().getGossipInterval());
      UdpActiveGossipMessage message = new UdpActiveGossipMessage();
      message.setUriFrom(gossipManager.getMyself().getUri().toASCIIString());
      message.setUuid(UUID.randomUUID().toString());
      message.getMembers().add(convert(me));
      for (LocalGossipMember other : memberList) {
        message.getMembers().add(convert(other));
      }
      byte[] json_bytes = MAPPER.writeValueAsString(message).getBytes();
      int packet_length = json_bytes.length;
      if (packet_length < GossipManager.MAX_PACKET_SIZE) {
        Response r = gossipCore.send(message, member.getUri());
        if (r instanceof ActiveGossipOk){
          //maybe count metrics here
        } else {
          LOGGER.warn("Message "+ message + " generated response "+ r);
        }
      } else {
        GossipService.LOGGER.error("The length of the to be send message is too large ("
                + packet_length + " > " + GossipManager.MAX_PACKET_SIZE + ").");
      }
    } catch (IOException e1) {
      GossipService.LOGGER.warn(e1);
    }
  }
  
  private GossipMember convert(LocalGossipMember member){
    GossipMember gm = new GossipMember();
    gm.setCluster(member.getClusterName());
    gm.setHeartbeat(member.getHeartbeat());
    gm.setUri(member.getUri().toASCIIString());
    gm.setId(member.getId());
    return gm;
  }
  
}
