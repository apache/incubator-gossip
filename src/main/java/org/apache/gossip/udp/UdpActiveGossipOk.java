package org.apache.gossip.udp;

import org.apache.gossip.model.ActiveGossipOk;

public class UdpActiveGossipOk extends ActiveGossipOk implements Trackable {


  private String uriFrom;
  private String uuid;
  
  public String getUriFrom() {
    return uriFrom;
  }
  
  public void setUriFrom(String uriFrom) {
    this.uriFrom = uriFrom;
  }
  
  public String getUuid() {
    return uuid;
  }
  
  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

}
