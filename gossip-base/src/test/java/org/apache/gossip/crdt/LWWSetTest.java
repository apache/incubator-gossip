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
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LWWSetTest {
  static private Clock clock = new SystemClock();
  private Set<Integer> sampleSet;

  @Before
  public void setup(){
    sampleSet = new HashSet<>();
    sampleSet.add(4);
    sampleSet.add(5);
    sampleSet.add(12);
  }

  @Test
  public void setConstructorTest(){
    Assert.assertEquals(new LWWSet<>(sampleSet).value(), sampleSet);
  }

  @Test
  public void stressWithSetTest(){
    Set<Integer> set = new HashSet<>();
    LWWSet<Integer> lww = new LWWSet<>();
    for (int it = 0; it < 100; it++){
      LWWSet<Integer> newLww;
      if (it % 5 == 1){
        //deleting existing
        Integer forDelete = set.stream().skip((long) (set.size() * Math.random())).findFirst().get();
        newLww = lww.remove(forDelete);
        Assert.assertEquals(lww.value(), set); // check old version is immutable
        set.remove(forDelete);
      } else {
        //adding
        Integer forAdd = (int) (10000 * Math.random());
        newLww = lww.add(forAdd);
        Assert.assertEquals(lww.value(), set); // check old version is immutable
        set.add(forAdd);
      }
      lww = newLww;
      Assert.assertEquals(lww.value(), set);
    }
  }

  @Test
  public void equalsTest(){
    LWWSet<Integer> lww = new LWWSet<>(sampleSet);
    Assert.assertFalse(lww.equals(sampleSet));
    LWWSet<Integer> newLww = lww.add(25);
    sampleSet.add(25);
    Assert.assertFalse(newLww.equals(lww));
    Assert.assertEquals(new LWWSet<>(sampleSet), newLww);
  }

  @Test
  public void valueTest() {
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
  public void removeMissingTest(){
    LWWSet<Integer> lww = new LWWSet<>(sampleSet);
    lww = lww.add(25);
    lww = lww.remove(25);
    Assert.assertEquals(lww.value(), sampleSet);
    lww = lww.remove(25);
    lww = lww.add(25);
    sampleSet.add(25);
    Assert.assertEquals(lww.value(), sampleSet);
  }

  @Test
  public void stressMergeTest(){
    // in one-process context, add, remove and merge operations of lww are equal to operations of Set
    // we've already checked it. Now just check merge
    Set<Integer> set1 = new HashSet<>(), set2 = new HashSet<>();
    LWWSet<Integer> lww1 = new LWWSet<>(), lww2 = new LWWSet<>();

    for (int it = 0; it < 100; it++){
      Integer forAdd = (int) (10000 * Math.random());
      if (it % 2 == 0){
        set1.add(forAdd);
        lww1 = lww1.add(forAdd);
      } else {
        set2.add(forAdd);
        lww2 = lww2.add(forAdd);
      }
    }
    Assert.assertEquals(lww1.value(), set1);
    Assert.assertEquals(lww2.value(), set2);
    Set<Integer> mergedSet = Stream.concat(set1.stream(), set2.stream()).collect(Collectors.toSet());
    Assert.assertEquals(lww1.merge(lww2).value(), mergedSet);
  }

  @Test
  public void fakeTimeMergeTest(){
    // try to create LWWSet with time from future (simulate other process with its own clock) and validate result
    // check remove from the future
    Map<Integer, LWWSet.Timestamps> map = new HashMap<>();
    long nano = clock.nanoTime();
    map.put(25, new LWWSet.Timestamps(nano, nano + 100000));
    LWWSet<Integer> lww = new LWWSet<>(map);
    Assert.assertEquals(lww, new LWWSet<Integer>());
    //create new LWWSet with element 25, and merge with other LWW which has remove in future
    Assert.assertEquals(new LWWSet<>(25).merge(lww).value(), new LWWSet<Integer>().value());

    // add in future
    map.put(25, new LWWSet.Timestamps(nano + 100000 + 100000, 0));
    lww = new LWWSet<>(map);
    lww = lww.remove(25);
    Assert.assertEquals(lww, new LWWSet<>(25)); // 25 is still here
  }

  @Test
  public void optimizeTest(){
    Assert.assertEquals(new LWWSet<>(sampleSet).value(), sampleSet);
    Assert.assertEquals(new LWWSet<>(sampleSet).optimize().value(), sampleSet);
  }
}
//LWWSetTest.
