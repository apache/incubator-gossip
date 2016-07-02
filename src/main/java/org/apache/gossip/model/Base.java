package org.apache.gossip.model;

import org.codehaus.jackson.annotate.JsonSubTypes;
import org.codehaus.jackson.annotate.JsonSubTypes.Type;
import org.codehaus.jackson.annotate.JsonTypeInfo;

@JsonTypeInfo(  
        use = JsonTypeInfo.Id.CLASS,  
        include = JsonTypeInfo.As.PROPERTY,  
        property = "type") 
@JsonSubTypes({
        @Type(value = ActiveGossipMessage.class, name = "ActiveGossipMessage"),
        @Type(value = ActiveGossipFault.class, name = "ActiveGossipFault"),
        @Type(value = ActiveGossipOk.class, name = "ActiveGossipOk")
        })
public class Base {

}
