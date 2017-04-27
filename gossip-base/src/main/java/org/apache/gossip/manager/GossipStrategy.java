package org.apache.gossip.manager;

public interface GossipStrategy {
  
  /** installs this particular gossip strategy on a gossiper */
  void install(ActiveGossiper gossiper);
}
