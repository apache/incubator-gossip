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

package org.apache.gossip.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.gossip.LocalMember;
import org.apache.gossip.Member;
import org.apache.gossip.consistency.Consistency;
import org.apache.gossip.consistency.ConsistencyLevel;
import org.apache.gossip.consistency.LocalRequestCallable;
import org.apache.gossip.consistency.RemoteRequestCallable;
import org.apache.gossip.model.DataRequestMessage;
import org.apache.gossip.model.Response;
import org.apache.log4j.Logger;

public class Coordinator {
	public static final Logger LOGGER = Logger.getLogger(Coordinator.class);
    ExecutorService executor;
    
    public Coordinator() {
    		executor = Executors.newCachedThreadPool();
    }
    
    private void cancelAll (List<Future<Response>> futures) {
		for(Future<Response> future : futures) {
			future.cancel(true);
		}
	}

    private List<Response> handleAll(List<Future<Response>> futures,
    		ExecutorCompletionService<Response> ecs) {
	    	List<Response> responses = new ArrayList<Response>();
	    	int i = 0;
	    	while(futures.size() > 0) {
	    		try {
	    			Future<Response> ft = ecs.take();
	    		    responses.add(ft.get());
	    		    i++;
	    		    futures.remove(ft);
	    		} catch(Exception ex) {
	    			cancelAll(futures);
	    			LOGGER.error(ex.getStackTrace().toString());
	    			LOGGER.error("Unable to satisfy consistency requirement ALL for this request as"
	    					+ " one of the nodes failed to return result");
	    			return null;
	    		}
	    	}
	    	return responses;
    }
    
    private List<Response> handleN(List<Future<Response>> futures,
    		ExecutorCompletionService<Response> ecs, Consistency con) {
	    	int availableResults = 0;
	    	int allowedFailures = futures.size() - (Integer)con.getParameters().get("n");
	    	List<Response> responses = new ArrayList<Response>();
	    	while(availableResults < (Integer)con.getParameters().get("n")) {
	    		try {
	    			Future<Response> ft = ecs.take();
	    		    responses.add(ft.get());
	    		    availableResults++;
	    		    futures.remove(ft);
	    		} catch(Exception ex) {
	    			allowedFailures--;
	    			if(allowedFailures < 0)
	    			{
	    				cancelAll(futures);
	        			System.out.println(ex.getStackTrace().toString());
	        			System.out.println("Not enough results available to support consistency level N with value"
	    			                 + Integer.toString((Integer)con.getParameters().get("n")));
	    				return null;
	    			}
	    		}
	    	}
	    	cancelAll(futures);
	    	return responses;
    }
    
    private List<Response> handleAny(List<Future<Response>> futures,
    			ExecutorCompletionService<Response> ecs) {
	    	int futureSize = futures.size();
	    	List<Response> responses = new ArrayList<Response>();
	    	while(futureSize > 0){
			try {
			    responses.add(ecs.take().get());
			    break;
			} catch(Exception ex) {
			}
			futureSize--;
	    	}
	    cancelAll(futures);
		if(responses.size() == 0) {
		    LOGGER.error("Couldnt get enough responses to satisfy consistency requirement ANY");
		    return null;
		}
	    	return responses;
    }
    
    public List<Response> coordinateRequest(List<? extends Member> members, DataRequestMessage request,
    		Consistency con, LocalMember me, final GossipCore gossipCore, final GossipManager gossipManager) {
    	ExecutorCompletionService<Response> ecs = new ExecutorCompletionService<Response>(executor);
    	List<Future<Response>> futures = new ArrayList<Future<Response>>();
    	for(Member member : members) {
    		if(member.getId() == me.getId()) {
    			LocalRequestCallable localRequest = new LocalRequestCallable(request, gossipManager);
    			futures.add(ecs.submit(localRequest));
    			continue;
    		}
    		RemoteRequestCallable remoteRequest = new RemoteRequestCallable(request, me, member, gossipCore);
    		futures.add(ecs.submit(remoteRequest));
    	}
    	if(con.getLevel() == ConsistencyLevel.ALL)
    		return handleAll(futures, ecs);
    	else if(con.getLevel() == ConsistencyLevel.N)
    		return handleN(futures, ecs, con);
    	else if(con.getLevel() == ConsistencyLevel.ANY)
    		return handleAny(futures, ecs);
    	return null;
    }
}