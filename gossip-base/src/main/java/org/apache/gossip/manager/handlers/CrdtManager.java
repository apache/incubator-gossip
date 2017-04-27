package org.apache.gossip.manager.handlers;

import org.apache.gossip.crdt.Crdt;
import org.apache.gossip.model.PerNodeDataMessage;
import org.apache.gossip.model.SharedDataMessage;

public interface CrdtManager {
  Crdt findCrdt(String key);
  Crdt merge(SharedDataMessage message);
  
  // todo: verify that these methods are not just testing artifacts.
  PerNodeDataMessage findPerNodeGossipData(String nodeId, String key);
  SharedDataMessage findSharedGossipData(String key);
}
