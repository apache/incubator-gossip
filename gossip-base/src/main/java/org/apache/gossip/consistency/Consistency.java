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

import java.util.HashMap;

public class Consistency {
	private ConsistencyLevel level;
	private HashMap<String, Object> parameters;
	
	public Consistency(ConsistencyLevel level, HashMap<String, Object> params) {
		this.level = level;
		this.parameters = params;
		if(this.parameters == null) {
			this.parameters = new HashMap<String, Object>();
		}
	}

	public ConsistencyLevel getLevel() {
		return level;
	}

	public void setLevel(ConsistencyLevel level) {
		this.level = level;
	}

	public HashMap<String, Object> getParameters() {
		return parameters;
	}

	public void setParameters(HashMap<String, Object> parameters) {
		this.parameters = parameters;
	}
	
	public void addParameter(String key, Object value) {
		this.parameters.put(key, value);
	}
}
