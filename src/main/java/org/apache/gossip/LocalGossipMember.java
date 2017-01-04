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
package org.apache.gossip;

import java.net.URI;

import javax.management.NotificationListener;

/**
 * This object represent a gossip member with the properties known locally. These objects are stored
 * in the local list of gossip member.s
 * 
 * @author harmenw
 */
public class LocalGossipMember extends GossipMember {
  /** The timeout timer for this gossip member. */
  private final transient GossipTimeoutTimer timeoutTimer;

  /**
   * Constructor.
   * 
   * @param uri
   *          The uri of the member
   * @param id
   *          id of the node
   * @param heartbeat
   *          The current heartbeat.
   * @param notificationListener
   * @param cleanupTimeout
   *          The cleanup timeout for this gossip member.
   */
  public LocalGossipMember(String clusterName, URI uri, String id,
          long heartbeat, NotificationListener notificationListener, int cleanupTimeout) {
    super(clusterName, uri, id, heartbeat);
    timeoutTimer = new GossipTimeoutTimer(cleanupTimeout, notificationListener, this);
  }

  /**
   * Start the timeout timer.
   */
  public void startTimeoutTimer() {
    timeoutTimer.start();
  }

  /**
   * Reset the timeout timer.
   */
  public void resetTimeoutTimer() {
    timeoutTimer.reset();
  }

  public void disableTimer() {
    timeoutTimer.removeAllNotifications();
    timeoutTimer.stop();
  }
}
