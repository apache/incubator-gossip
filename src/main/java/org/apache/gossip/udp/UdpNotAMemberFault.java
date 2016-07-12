package org.apache.gossip.udp;

import org.apache.gossip.model.NotAMemberFault;

public class UdpNotAMemberFault extends NotAMemberFault implements Trackable{

  public UdpNotAMemberFault(){
    
  }
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
