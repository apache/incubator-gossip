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
import java.util.List;

import org.apache.gossip.model.DataResponse;
import org.apache.gossip.model.Response;

public class MajorityResponseMerger implements ResponseMerger {
    public Object merge(List<? extends Response> responses) {
    	HashMap<Object, Integer> responseCount = new HashMap<Object, Integer>();
    	int majorityCount = 0;
    	Object majorityResult = null;
    	for(Response response : responses) {
    		DataResponse r = (DataResponse) response;
    		if(responseCount.containsKey(r.getValue())) {
    			responseCount.put(r.getValue(), responseCount.get(r.getValue()) + 1);
    		} else {
    			responseCount.put(r.getValue(), 1);
    		}
    		if(majorityCount < responseCount.get(r.getValue())) {
    			majorityCount = responseCount.get(r.getValue());
    			majorityResult = r.getValue();
    		}
    	}
    return majorityResult;
    }
}
