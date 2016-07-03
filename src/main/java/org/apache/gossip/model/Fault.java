package org.apache.gossip.model;

public class Fault extends Response {

  private String exception;

  public Fault(){}

  public String getException() {
    return exception;
  }

  public void setException(String exception) {
    this.exception = exception;
  }

  @Override
  public String toString() {
    return "Fault [exception=" + exception + "]";
  }

}

