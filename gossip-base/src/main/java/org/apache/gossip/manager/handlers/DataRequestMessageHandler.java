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

package org.apache.gossip.manager.handlers;

import java.net.URI;
import java.util.NavigableSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.gossip.LocalMember;
import org.apache.gossip.Member;
import org.apache.gossip.event.data.DataEventConstants;
import org.apache.gossip.manager.GossipCore;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.model.Base;
import org.apache.gossip.model.RequestAction;
import org.apache.gossip.udp.UdpDataRequestMessage;
import org.apache.gossip.udp.UdpDataResponse;
import org.apache.log4j.Logger;

public class DataRequestMessageHandler implements MessageHandler {
	public static final Logger LOGGER = Logger.getLogger(DataRequestMessageHandler.class);

	private final BlockingQueue<Runnable> requestHandlerQueue;
	private final ExecutorService requestEventExecutor;

	public DataRequestMessageHandler() {
		requestHandlerQueue = new ArrayBlockingQueue<>(DataEventConstants.REQUEST_NOTIFIER_QUEUE_SIZE);
		requestEventExecutor = new ThreadPoolExecutor(DataEventConstants.REQUEST_NOTIFIER_CORE_POOL_SIZE,
	            DataEventConstants.REQUEST_NOTIFIER_MAX_POOL_SIZE,
	            DataEventConstants.REQUEST_NOTIFIER_KEEP_ALIVE_TIME_SECONDS, TimeUnit.SECONDS,
	            requestHandlerQueue, new ThreadPoolExecutor.DiscardOldestPolicy());
	}
	
	private URI getUriFromId(String id, GossipManager gossipManager) {
		NavigableSet<LocalMember> members = gossipManager.getMembers().keySet();
		for(Member member : members) {
			if(member.getId().equals(id))
				return member.getUri();
		}
		return null;
	}

	public boolean invoke(GossipCore gossipCore, GossipManager gossipManager,
			Base base) {
		if(gossipManager.getDataReadHandler() == null) {
			LOGGER.warn("No handlers registered for read. This operation is not supported");
			//TODO: throw exception?
		} else {
			UdpDataRequestMessage request = (UdpDataRequestMessage)base;
			if(request.getAction() == RequestAction.READ) {
				requestEventExecutor.execute(() -> onRead(request, gossipCore, gossipManager));
			} else if(request.getAction() == RequestAction.WRITE) {
				requestEventExecutor.execute(() -> onWrite(request, gossipCore, gossipManager));
			}
		}
		return true;
	}
	
	private void onRead(UdpDataRequestMessage request, GossipCore gossipCore, GossipManager gossipManager) {
		if(LOGGER.isDebugEnabled())
			LOGGER.debug("received read message" + request.toString());
		Object value = gossipManager.getDataReadHandler().read(request.getKey());
		UdpDataResponse rwResponse = new UdpDataResponse();
		rwResponse.setKey(request.getKey());
		rwResponse.setValue(value);
		rwResponse.setUuid(request.getUuid());
		rwResponse.setUriFrom(request.getUriFrom());
		URI uri = getUriFromId(request.getUriFrom(), gossipManager);
		if(uri == null) {
			LOGGER.error("Cant find a member with the id to send a response");
		} else {
			LOGGER.error("sending response as " + rwResponse.toString() + "to " + uri.toString());
		    gossipCore.sendOneWay(rwResponse, uri);
		}
	}
	
	private void onWrite(UdpDataRequestMessage request, GossipCore gossipCore, GossipManager gossipManager) {
		if(LOGGER.isDebugEnabled())
			LOGGER.debug("received read message" + request.toString());
		boolean written = gossipManager.getDataWriteHandler().write(request.getKey(), request.getValue());
		UdpDataResponse rwResponse = new UdpDataResponse();
		rwResponse.setKey(request.getKey());
		rwResponse.setValue(written);
		rwResponse.setUuid(request.getUuid());
		rwResponse.setUriFrom(request.getUriFrom());
		URI uri = getUriFromId(request.getUriFrom(), gossipManager);
		if(uri == null) {
			LOGGER.error("Cant find a member with the id to send a response");
		} else {
			LOGGER.error("sending write response as " + rwResponse.toString() + "to " + uri.toString());
		    gossipCore.sendOneWay(rwResponse, uri);
		}
	}
    
}
