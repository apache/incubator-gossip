package org.apache.gossip.model;

import java.util.ArrayList;
import java.util.List;

public class ActiveGossipMessage extends Message {

  private List<GossipMember> members = new ArrayList<>();
  
  public ActiveGossipMessage(){
    
  }

  public List<GossipMember> getMembers() {
    return members;
  }

  public void setMembers(List<GossipMember> members) {
    this.members = members;
  }
  
}
