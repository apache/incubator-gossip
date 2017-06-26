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

import org.apache.gossip.manager.Clock;
import org.apache.gossip.manager.SystemClock;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LWWSetTest extends AbstractCRDTStringSetTest<LWWSet<String>> {
  static private Clock clock = new SystemClock();

  LWWSet<String> construct(Set<String> set){
    return new LWWSet<>(set);
  }

  LWWSet<String> construct(){
    return new LWWSet<>();
  }

  @Test
  public void valueTest(){
    Map<Character, LWWSet.Timestamps> map = new HashMap<>();
    map.put('a', new LWWSet.Timestamps(1, 0));
    map.put('b', new LWWSet.Timestamps(1, 2));
    map.put('c', new LWWSet.Timestamps(3, 3));
    Set<Character> toTest = new HashSet<>();
    toTest.add('a'); // for 'a' addTime > removeTime
    toTest.add('c'); // for 'c' times are equal, we prefer add to remove
    Assert.assertEquals(new LWWSet<>(map).value(), toTest);
    Assert.assertEquals(new LWWSet<>(map), new LWWSet<>('a', 'c'));
  }

  @Test
  public void fakeTimeMergeTest(){
    // try to create LWWSet with time from future (simulate other process with its own clock) and validate result
    // check remove from the future
    Map<Integer, LWWSet.Timestamps> map = new HashMap<>();
    map.put(25, new LWWSet.Timestamps(clock.nanoTime(), Long.MAX_VALUE));
    LWWSet<Integer> lww = new LWWSet<>(map);
    Assert.assertEquals(lww, new LWWSet<Integer>());
    //create new LWWSet with element 25, and merge with other LWW which has remove in future
    Assert.assertEquals(new LWWSet<>(25).merge(lww), new LWWSet<Integer>());

    // add in future
    map.put(25, new LWWSet.Timestamps(Long.MAX_VALUE, 0));
    lww = new LWWSet<>(map);
    lww = lww.remove(25);
    Assert.assertEquals(lww, new LWWSet<>(25)); // 25 is still here
  }
}
