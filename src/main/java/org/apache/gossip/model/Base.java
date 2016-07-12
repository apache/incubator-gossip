package org.apache.gossip.model;

import org.apache.gossip.udp.UdpActiveGossipMessage;
import org.apache.gossip.udp.UdpActiveGossipOk;
import org.apache.gossip.udp.UdpNotAMemberFault;
import org.codehaus.jackson.annotate.JsonSubTypes;
import org.codehaus.jackson.annotate.JsonSubTypes.Type;
import org.codehaus.jackson.annotate.JsonTypeInfo;

@JsonTypeInfo(  
        use = JsonTypeInfo.Id.CLASS,  
        include = JsonTypeInfo.As.PROPERTY,  
        property = "type") 
@JsonSubTypes({
        @Type(value = ActiveGossipMessage.class, name = "ActiveGossipMessage"),
        @Type(value = Fault.class, name = "Fault"),
        @Type(value = ActiveGossipOk.class, name = "ActiveGossipOk"),
        @Type(value = UdpActiveGossipOk.class, name = "UdpActiveGossipOk"),
        @Type(value = UdpActiveGossipMessage.class, name = "UdpActiveGossipMessage"),
        @Type(value = UdpNotAMemberFault.class, name = "UdpNotAMemberFault")
        })
public class Base {

}
