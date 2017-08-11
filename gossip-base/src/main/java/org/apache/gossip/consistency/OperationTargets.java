package org.apache.gossip.consistency;

import org.apache.gossip.LocalMember;

import java.util.List;

public interface OperationTargets {
    /**
     *
     * @param key
     * @param me
     * @param living
     * @param dead
     * @return list of targets
     */
    List<LocalMember> generateTargets(String key, LocalMember me, List<LocalMember> living, List<LocalMember> dead);
}
