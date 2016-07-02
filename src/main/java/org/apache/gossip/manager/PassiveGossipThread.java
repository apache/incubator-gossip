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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.gossip.GossipMember;
import org.apache.gossip.GossipService;
import org.apache.gossip.model.Base;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.apache.gossip.RemoteGossipMember;

/**
 * [The passive thread: reply to incoming gossip request.] This class handles the passive cycle,
 * where this client has received an incoming message. For now, this message is always the
 * membership list, but if you choose to gossip additional information, you will need some logic to
 * determine the incoming message.
 */
abstract public class PassiveGossipThread implements Runnable {

  public static final Logger LOGGER = Logger.getLogger(PassiveGossipThread.class);

  /** The socket used for the passive thread of the gossip service. */
  private final DatagramSocket server;

  private final GossipManager gossipManager;

  private final AtomicBoolean keepRunning;

  private final String cluster;
  
  private final ObjectMapper MAPPER = new ObjectMapper();
  
  private GossipCore gossipCore;

  public PassiveGossipThread(GossipManager gossipManager) {
    this.gossipManager = gossipManager;
    gossipCore = new GossipCore(gossipManager);
    try {
      SocketAddress socketAddress = new InetSocketAddress(gossipManager.getMyself().getUri().getHost(),
              gossipManager.getMyself().getUri().getPort());
      server = new DatagramSocket(socketAddress);
      LOGGER.debug("Gossip service successfully initialized on port "
              + gossipManager.getMyself().getUri().getPort());
      LOGGER.debug("I am " + gossipManager.getMyself());
      cluster = gossipManager.getMyself().getClusterName();
      if (cluster == null){
        throw new IllegalArgumentException("cluster was null");
      }
    } catch (SocketException ex) {
      LOGGER.warn(ex);
      throw new RuntimeException(ex);
    }
    keepRunning = new AtomicBoolean(true);
  }

  @Override
  public void run() {
    while (keepRunning.get()) {
      try {
        byte[] buf = new byte[server.getReceiveBufferSize()];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        server.receive(p);
        int packet_length = UdpUtil.readPacketLengthFromBuffer(buf);
        if (packet_length <= GossipManager.MAX_PACKET_SIZE) {
          byte[] json_bytes = new byte[packet_length];
          for (int i = 0; i < packet_length; i++) {
            json_bytes[i] = buf[i + 4];
          }
          debug(packet_length, json_bytes);
          try {
            Base activeGossipMessage = MAPPER.readValue(json_bytes, Base.class);
            gossipCore.recieve(activeGossipMessage);
          } catch (RuntimeException ex) {//TODO trap json exception
            LOGGER.error("Unable to process message", ex);
          }
        } else {
          LOGGER.error("The received message is not of the expected size, it has been dropped.");
        }

      } catch (IOException e) {
        LOGGER.error(e);
        keepRunning.set(false);
      }
    }
    shutdown();
  }

  private void debug(int packetLength, byte[] jsonBytes) {
    if (GossipService.LOGGER.isDebugEnabled()){
      String receivedMessage = new String(jsonBytes);
      GossipService.LOGGER.debug("Received message (" + packetLength + " bytes): "
            + receivedMessage);
    }
  }

  public void shutdown() {
    try {
      server.close();
    } catch (RuntimeException ex) {
    }
  }

  /**
   * Abstract method for merging the local and remote list.
   * 
   * @param gossipManager
   *          The GossipManager for retrieving the local members and dead members list.
   * @param senderMember
   *          The member who is sending this list, this could be used to send a response if the
   *          remote list contains out-dated information.
   * @param remoteList
   *          The list of members known at the remote side.
   */
  abstract protected void mergeLists(GossipManager gossipManager, RemoteGossipMember senderMember,
          List<GossipMember> remoteList);
}