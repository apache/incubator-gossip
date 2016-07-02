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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;

import org.apache.gossip.GossipService;
import org.apache.gossip.LocalGossipMember;
import org.apache.gossip.manager.ActiveGossipThread;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.model.ActiveGossipMessage;
import org.apache.gossip.model.GossipMember;
import org.codehaus.jackson.map.ObjectMapper;

public class RandomActiveGossipThread extends ActiveGossipThread {

  protected ObjectMapper om = new ObjectMapper();
  
  /** The Random used for choosing a member to gossip with. */
  private final Random random;

  public RandomActiveGossipThread(GossipManager gossipManager) {
    super(gossipManager);
    random = new Random();
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
      InetAddress dest = InetAddress.getByName(member.getUri().getHost());
      ActiveGossipMessage message = new ActiveGossipMessage();
      message.getMembers().add(convert(me));
      for (LocalGossipMember other : memberList) {
        message.getMembers().add(convert(other));
      }
      byte[] json_bytes = om.writeValueAsString(message).getBytes();
      int packet_length = json_bytes.length;
      if (packet_length < GossipManager.MAX_PACKET_SIZE) {
        byte[] buf = createBuffer(packet_length, json_bytes);
        DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length, dest, member.getUri().getPort());
        socket.send(datagramPacket);
        
      } else {
        GossipService.LOGGER.error("The length of the to be send message is too large ("
                + packet_length + " > " + GossipManager.MAX_PACKET_SIZE + ").");
      }
    } catch (IOException e1) {
      GossipService.LOGGER.warn(e1);
    }
  }

  private byte[] createBuffer(int packetLength, byte[] jsonBytes) {
    byte[] lengthBytes = new byte[4];
    lengthBytes[0] = (byte) (packetLength >> 24);
    lengthBytes[1] = (byte) ((packetLength << 8) >> 24);
    lengthBytes[2] = (byte) ((packetLength << 16) >> 24);
    lengthBytes[3] = (byte) ((packetLength << 24) >> 24);
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 + jsonBytes.length);
    byteBuffer.put(lengthBytes);
    byteBuffer.put(jsonBytes);
    byte[] buf = byteBuffer.array();
    return buf;
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
