package org.apache.gossip.manager;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.gossip.GossipMember;
import org.apache.gossip.LocalGossipMember;
import org.apache.gossip.RemoteGossipMember;
import org.apache.gossip.model.ActiveGossipFault;
import org.apache.gossip.model.ActiveGossipMessage;
import org.apache.gossip.model.ActiveGossipOk;
import org.apache.gossip.model.Base;
import org.apache.gossip.model.Message;
import org.apache.gossip.model.Response;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

public class GossipCore {
  
  private final GossipManager gossipManager;
  public static final Logger LOGGER = Logger.getLogger(GossipCore.class);

  public GossipCore(GossipManager manager){
    this.gossipManager = manager;
  }
  
  public void recieve(Base base){
    if (base instanceof Response){
      System.out.println("gory response" + base);
    }
    if (base instanceof ActiveGossipMessage){
      List<GossipMember> remoteGossipMembers = new ArrayList<>();
      RemoteGossipMember senderMember = null;
      ActiveGossipMessage activeGossipMessage = (ActiveGossipMessage) base;
      for (int i = 0; i < activeGossipMessage.getMembers().size(); i++) {
        URI u = null;
        try {
          u = new URI(activeGossipMessage.getMembers().get(i).getUri());
        } catch (URISyntaxException e) {
          LOGGER.debug("Gossip message with faulty URI", e);
          continue;
        }
        RemoteGossipMember member = new RemoteGossipMember(
                activeGossipMessage.getMembers().get(i).getCluster(),
                u,
                activeGossipMessage.getMembers().get(i).getId(),
                activeGossipMessage.getMembers().get(i).getHeartbeat());
       
        // This is the first member found, so this should be the member who is communicating
        // with me.
        if (i == 0) {
          senderMember = member;
        } 
        if (!(member.getClusterName().equals(gossipManager.getMyself().getClusterName()))){
          LOGGER.warn("Not a member of this cluster " + i);
          ActiveGossipFault f = new ActiveGossipFault();
          f.setException("Not a member of this cluster");
          sendOneWay(f, member.getUri());

          continue;
        }
        remoteGossipMembers.add(member);
        ActiveGossipOk o = new ActiveGossipOk();
        sendOneWay(o, member.getUri());
      }

      mergeLists(gossipManager, senderMember, remoteGossipMembers);
    }
  }
  
  ObjectMapper mapper = new ObjectMapper();
  public Response send(Base message, LocalGossipMember partner){
    /*
    byte[] json_bytes = mapper.writeValueAsString(message).getBytes();
    int packet_length = json_bytes.length;
    if (packet_length < GossipManager.MAX_PACKET_SIZE) {
      byte[] buf = UdpUtil.createBuffer(packet_length, json_bytes);
      try (DatagramSocket socket = new DatagramSocket()) {
        InetAddress dest = InetAddress.getByName(partner.getUri().getHost());
        DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length, dest, partner.getUri().getPort());
        socket.send(datagramPacket);
      } 
    }*/
    return null;
  }
  
  public void sendOneWay(Base message, URI u){
    byte[] json_bytes;
    try {
      json_bytes = mapper.writeValueAsString(message).getBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    int packet_length = json_bytes.length;
    if (packet_length < GossipManager.MAX_PACKET_SIZE) {
      byte[] buf = UdpUtil.createBuffer(packet_length, json_bytes);
      try (DatagramSocket socket = new DatagramSocket()) {
        InetAddress dest = InetAddress.getByName(u.getHost());
        DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length, dest, u.getPort());
        socket.send(datagramPacket);
      } catch (IOException ex) { }
    }
  }
  

  /**
   * Merge remote list (received from peer), and our local member list. Simply, we must update the
   * heartbeats that the remote list has with our list. Also, some additional logic is needed to
   * make sure we have not timed out a member and then immediately received a list with that member.
   * 
   * @param gossipManager
   * @param senderMember
   * @param remoteList
   * 
   * COPIED FROM PASSIVE GOSSIP THREAD
   */
  protected void mergeLists(GossipManager gossipManager, RemoteGossipMember senderMember,
          List<GossipMember> remoteList) {

    // if the person sending to us is in the dead list consider them up
    for (LocalGossipMember i : gossipManager.getDeadList()) {
      if (i.getId().equals(senderMember.getId())) {
        LOGGER.info(gossipManager.getMyself() + " contacted by dead member " + senderMember.getUri());
        LocalGossipMember newLocalMember = new LocalGossipMember(senderMember.getClusterName(),
                senderMember.getUri(), senderMember.getId(),
                senderMember.getHeartbeat(), gossipManager, gossipManager.getSettings()
                        .getCleanupInterval());
        gossipManager.revivieMember(newLocalMember);
        newLocalMember.startTimeoutTimer();
      }
    }
    for (GossipMember remoteMember : remoteList) {
      if (remoteMember.getId().equals(gossipManager.getMyself().getId())) {
        continue;
      }
      if (gossipManager.getMemberList().contains(remoteMember)) {
        LocalGossipMember localMember = gossipManager.getMemberList().get(
                gossipManager.getMemberList().indexOf(remoteMember));
        if (remoteMember.getHeartbeat() > localMember.getHeartbeat()) {
          localMember.setHeartbeat(remoteMember.getHeartbeat());
          localMember.resetTimeoutTimer();
        }
      } else if (!gossipManager.getMemberList().contains(remoteMember)
              && !gossipManager.getDeadList().contains(remoteMember)) {
        LocalGossipMember newLocalMember = new LocalGossipMember(remoteMember.getClusterName(),
                remoteMember.getUri(), remoteMember.getId(),
                remoteMember.getHeartbeat(), gossipManager, gossipManager.getSettings()
                        .getCleanupInterval());
        gossipManager.createOrRevivieMember(newLocalMember);
        newLocalMember.startTimeoutTimer();
      } else {
        if (gossipManager.getDeadList().contains(remoteMember)) {
          LocalGossipMember localDeadMember = gossipManager.getDeadList().get(
                  gossipManager.getDeadList().indexOf(remoteMember));
          if (remoteMember.getHeartbeat() > localDeadMember.getHeartbeat()) {
            LocalGossipMember newLocalMember = new LocalGossipMember(remoteMember.getClusterName(),
                    remoteMember.getUri(), remoteMember.getId(),
                    remoteMember.getHeartbeat(), gossipManager, gossipManager.getSettings()
                            .getCleanupInterval());
            gossipManager.revivieMember(newLocalMember);
            newLocalMember.startTimeoutTimer();
            LOGGER.debug("Removed remote member " + remoteMember.getAddress()
                    + " from dead list and added to local member list.");
          } else {
            LOGGER.debug("me " + gossipManager.getMyself());
            LOGGER.debug("sender " + senderMember);
            LOGGER.debug("remote " + remoteList);
            LOGGER.debug("live " + gossipManager.getMemberList());
            LOGGER.debug("dead " + gossipManager.getDeadList());
          }
        } else {
          LOGGER.debug("me " + gossipManager.getMyself());
          LOGGER.debug("sender " + senderMember);
          LOGGER.debug("remote " + remoteList);
          LOGGER.debug("live " + gossipManager.getMemberList());
          LOGGER.debug("dead " + gossipManager.getDeadList());
          // throw new IllegalArgumentException("wtf");
        }
      }
    }
  }

  
}
