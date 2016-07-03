package org.apache.gossip.udp;

import org.apache.gossip.model.ActiveGossipMessage;

public class UdpActiveGossipMessage extends ActiveGossipMessage implements Trackable {

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

  @Override
  public String toString() {
    return "UdpActiveGossipMessage [uriFrom=" + uriFrom + ", uuid=" + uuid + "]";
  }
  
}
