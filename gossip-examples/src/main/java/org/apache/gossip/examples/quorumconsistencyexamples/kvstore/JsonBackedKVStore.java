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
package org.apache.gossip.examples.quorumconsistencyexamples.kvstore;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonBackedKVStore {
	
	private ConcurrentHashMap<String, Object> kvStore;
	final private String fileName;
	ScheduledExecutorService scheduledService;
	
	public ConcurrentHashMap<String, Object> getKvStore() {
		return kvStore;
	}

	public void setKvStore(ConcurrentHashMap<String, Object> kvStore) {
		this.kvStore = kvStore;
	}

	public String getFileName() {
		return fileName;
	}

	/*
	 * read json file and initialize in memory kv store
	 * create a thread to sync json with in memory store
	 */
	public JsonBackedKVStore(String jsonFile) {
		fileName = jsonFile;
		kvStore = null;
		ObjectMapper mapper = new ObjectMapper();
		File file = new File(fileName);
		try {
			kvStore = mapper.readValue(file.read(), ConcurrentHashMap.class);
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		DataBackup backup = new DataBackup(this);
		scheduledService = Executors.newScheduledThreadPool(1);
		scheduledService.scheduleAtFixedRate(backup, 5, 5, TimeUnit.SECONDS);
	}
	
	public Object read(String key) {
		if (kvStore == null)
			return null;
		if(kvStore.containsKey(key)) {
			return kvStore.get(key);
		}
		return null;
	}
	
	public boolean write(Object value, String key) {
		if(kvStore == null)
			return false;
		kvStore.put(key, value);
		return true;
	}
}
