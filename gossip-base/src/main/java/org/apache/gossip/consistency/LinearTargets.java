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

package org.apache.gossip.consistency;

import org.apache.gossip.LocalMember;

import java.util.*;

/**
 * Order live nodes (including oneself) into a "linear" structure, by sorting based on id. Uses the key to select
 * n psuedo random nodes from the list. Those should be the targets of the operation
 */
public class LinearTargets implements OperationTargets {
  private int numberOfReplicas;

  public LinearTargets(int numberOfReplicas) {
    this.numberOfReplicas = numberOfReplicas;
  }

  @Override
  public List<LocalMember> generateTargets(String key, LocalMember me, List<LocalMember> living, List<LocalMember> dead) {
	living = new ArrayList<>(living);
    if (numberOfReplicas > living.size() + 1) {
      throw new RuntimeException("Not enough live nodes");
    }
    living.add(me);
    Collections.sort(living, (left, right) ->
        left.getId().compareTo(right.getId()));
    int start = key.hashCode() % living.size();
    List<LocalMember> targets = new ArrayList<>(numberOfReplicas);
    for (int i = 0; i < numberOfReplicas; i++) {
      targets.add(living.get(start++));
      if (start == living.size()) {
        start = 0;
      }
    }
    return targets;
  }

}
