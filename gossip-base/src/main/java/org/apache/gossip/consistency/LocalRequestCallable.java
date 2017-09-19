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

import java.util.concurrent.Callable;

import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.model.DataRequestMessage;
import org.apache.gossip.model.DataResponse;
import org.apache.gossip.model.RequestAction;
import org.apache.gossip.model.Response;
import org.apache.gossip.udp.UdpDataResponse;

public class LocalRequestCallable implements Callable<Response> {
	DataRequestMessage request;
	GossipManager gossipManager;

	public LocalRequestCallable(DataRequestMessage request, GossipManager gossipManager) {
		this.request = request;
		this.gossipManager = gossipManager;
	}

	public Response call() throws Exception {
		DataResponse response = null;
		if (request.getAction() == RequestAction.READ) {
			Object value = gossipManager.getDataReadHandler().read(request.getKey());
			response = new UdpDataResponse();
			response.setKey(request.getKey());
			response.setValue(value);
		} else if (request.getAction() == RequestAction.WRITE) {
			boolean written = gossipManager.getDataWriteHandler().write(request.getKey(), request.getValue());
			response = new UdpDataResponse();
			response.setKey(request.getKey());
			response.setValue(written);
		}
		return response;
	}

}
