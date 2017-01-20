package org.apache.gossip.manager;

public interface GossipCoreConstants {
  String WORKQUEUE_SIZE = "gossip.core.workqueue.size";
  String PER_NODE_DATA_SIZE = "gossip.core.pernodedata.size"; 
  String SHARED_DATA_SIZE = "gossip.core.shareddata.size";
  String REQUEST_SIZE = "gossip.core.requests.size";
  String THREADPOOL_ACTIVE = "gossip.core.threadpool.active";
  String THREADPOOL_SIZE = "gossip.core.threadpool.size";
  String MESSAGE_SERDE_EXCEPTION = "gossip.core.message_serde_exception";
  String MESSAGE_TRANSMISSION_EXCEPTION = "gossip.core.message_transmission_exception";
  String MESSAGE_TRANSMISSION_SUCCESS = "gossip.core.message_transmission_success";
}
