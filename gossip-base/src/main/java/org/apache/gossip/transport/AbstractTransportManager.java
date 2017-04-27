/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.gossip.transport;

import org.apache.gossip.manager.ClusterModel;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manage the active and passive gossipers. Notify on incoming messages
 */
public abstract class AbstractTransportManager implements TransportManager {
  
  public static final Logger LOGGER = Logger.getLogger(AbstractTransportManager.class);
  
  private final InterruptibleThread passiveGossipThread;
  
  private final List<BytesListener> bytesListeners = new ArrayList<>();
  
  public AbstractTransportManager(ClusterModel gossipManager) {
    passiveGossipThread = new InterruptibleThread(
        this::passiveLoop,
        String.format("passive-%s-%s", gossipManager.getMyself().getClusterName(), gossipManager.getMyself().getId()));
  }

  // shut down threads etc.
  @Override
  public void shutdown() {
    try {
      boolean result = passiveGossipThread.shutdown(100);
      if (!result) {
        // common when blocking patterns are used to read data from a socket.
        LOGGER.warn("executor shutdown timed out");
      }
    } catch (InterruptedException e) {
      LOGGER.error(e);
    }
  }

  @Override
  public void startEndpoint() {
    passiveGossipThread.start();
  }
  
  public void addBytesListener(BytesListener bl) {
    bytesListeners.add(bl);
  }
  
  // used to be inside PassiveGossipThread.
  private void passiveLoop() {
    while (passiveGossipThread.isRunning()) {
      try {
        // reconstruct message, then notify listeners.
        byte[] buf = read();
        bytesListeners.forEach(h -> {
          try {
            h.bytesReceived(buf);
          } catch (IOException ex) {
            logAndQuit(ex);
          }
        });
      } catch (IOException e) {
        logAndQuit(e);
      }
    }
  }
  
  private void logAndQuit(Exception e) {
    // InterruptedException are completely normal here because of the blocking lifecycle.
    if (!(e.getCause() instanceof InterruptedException)) {
      LOGGER.error(e);
    }
    passiveGossipThread.requestShutdown();
  }
}
