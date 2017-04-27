package org.apache.gossip.transport;

import org.apache.gossip.model.Base;

/** for classes that are interested in being notified of new messages. */
public interface MessageListener {
  
  /** a message has been received. do something with it. no syncrhonous/asyncrhonous guarantees */
  void messageReceived(Base message);
}
