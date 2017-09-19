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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.gossip.LocalMember;

/**
 * Order live nodes (including oneself) based on their distance. Each node has a latitude
 * and a longitude. The distance between two nodes is the euclidean distance between their
 * co-ordinates.
 */
public class NearestTargets implements OperationTargets {
	private int numberOfReplicas;

	public NearestTargets(int numberOfReplicas) {
		this.numberOfReplicas = numberOfReplicas;
	}

	private List<LocalMember> getNNearestNodes(LocalMember me, List<LocalMember> members) {
		Map<Double, LocalMember> map = new TreeMap<Double, LocalMember>();
		List<LocalMember> nearestNodes = new ArrayList<LocalMember>();
		for(int i = 0; i < members.size(); i++) {
			Map<String, String> props = members.get(i).getProperties();
			double dx = Double.parseDouble(me.getProperties().get("latitude"))
					    - Double.parseDouble(props.get("latitude"));
			double dy = Double.parseDouble(me.getProperties().get("longitude"))
					    - Double.parseDouble(props.get("longitude"));
			double dist = Math.sqrt((dx * dx) + (dy * dy));
			map.put(new Double(dist), members.get(i));
		}
		int n = numberOfReplicas;
		for(Map.Entry<Double,LocalMember> entry : map.entrySet()) {
	        nearestNodes.add(entry.getValue());
	        n--;
	        if (n == 0) {
	        		break;
	        }
	    }
		return nearestNodes;
	}

	private boolean isValidNode(LocalMember node) {
		if(node.getProperties().containsKey("longitude") && node.getProperties().containsKey("latitude"))
			return true;
		return false;
	}
	
	private boolean enoughValidNodes(List<LocalMember> members) {
		if(members.size() < this.numberOfReplicas)
			return false;
		return true;
	}
	
	private List<LocalMember> getLivingNodesWithCoordinates(List<LocalMember> living, LocalMember me) {
		List<LocalMember> membersWithCoordinates = new ArrayList<LocalMember>();
		for(int i = 0; i < living.size(); i++) {
			if(isValidNode(living.get(i))) {
				membersWithCoordinates.add(living.get(i));
			}
		}
		return membersWithCoordinates;
	}

	public List<LocalMember> generateTargets(String key, LocalMember me,
			List<LocalMember> living, List<LocalMember> dead) {
		if(!isValidNode(me))
			throw new RuntimeException("Current node " + me.toString() + "doesnt have longitude and latitude properties");
		List<LocalMember> membersWithCoordinates = getLivingNodesWithCoordinates(living, me);
		membersWithCoordinates.add(me);
		if (!enoughValidNodes(membersWithCoordinates)) {
			throw new RuntimeException("Not enough live nodes with longitude and latitude properties");
		}
		return getNNearestNodes(me, membersWithCoordinates);
	}

}
