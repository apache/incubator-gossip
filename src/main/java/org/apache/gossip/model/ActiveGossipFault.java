package org.apache.gossip.model;

public class ActiveGossipFault extends Response{

  private String exception;

  public ActiveGossipFault(){}

  public String getException() {
    return exception;
  }

  public void setException(String exception) {
    this.exception = exception;
  }

  @Override
  public String toString() {
    return "ActiveGossipFault [exception=" + exception + "]";
  }

}

