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
