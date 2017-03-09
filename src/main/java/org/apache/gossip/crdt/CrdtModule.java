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
package org.apache.gossip.crdt;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;

abstract class OrSetMixin<E> {
  @JsonCreator
  OrSetMixin(@JsonProperty("elements") Map<E, Set<UUID>> w, @JsonProperty("tombstones") Map<E, Set<UUID>> h) { }
  @JsonProperty("elements") abstract Map<E, Set<UUID>> getElements();
  @JsonProperty("tombstones") abstract Map<E, Set<UUID>> getTombstones();
  @JsonIgnore abstract boolean isEmpty();
}

abstract class GrowOnlySetMixin<E>{
  @JsonCreator
  GrowOnlySetMixin(@JsonProperty("elements") Set<E> elements){ }
  @JsonProperty("elements") abstract Set<E> getElements();
  @JsonIgnore abstract boolean isEmpty();
}

abstract class GrowOnlyCounterMixin{
  @JsonCreator
  GrowOnlyCounterMixin(@JsonProperty("counters") Map<String , Long> counters){ }
  @JsonProperty("counters") abstract Map<String , Long> getCounters();
}

//If anyone wants to take a stab at this. please have at it
//https://github.com/FasterXML/jackson-datatype-guava/blob/master/src/main/java/com/fasterxml/jackson/datatype/guava/ser/MultimapSerializer.java
public class CrdtModule extends SimpleModule {

  private static final long serialVersionUID = 6134836523275023418L;

  public CrdtModule() {
    super("CrdtModule", new Version(0, 0, 0, "0.0.0", "org.apache.gossip", "gossip"));
  }

  @Override
  public void setupModule(SetupContext context) {
    context.setMixInAnnotations(OrSet.class, OrSetMixin.class);
    context.setMixInAnnotations(GrowOnlySet.class, GrowOnlySetMixin.class);
    context.setMixInAnnotations(GrowOnlyCounter.class, GrowOnlyCounterMixin.class);
  }

}

