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

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class GrowOnlyCounterTest {

  @Test
  public void mergeTest(){

    String node1Id = "0";
    String node2Id = "1";
    String node3Id = "2";

    GrowOnlyCounter gCounter1 = new GrowOnlyCounter(node1Id);
    GrowOnlyCounter gCounter2 = new GrowOnlyCounter(node2Id);
    GrowOnlyCounter gCounter3 = new GrowOnlyCounter(node3Id);

    // Node 1 increase count by 3
    gCounter1.increaseBy(3);

    // Node 2 increase count
    gCounter2.increase();

    // Node 3 increase count by 2
    gCounter3.increaseBy(2);

    // After node 2 receive from node 1
    gCounter2 = gCounter2.merge(gCounter1);
    Assert.assertEquals(4, (long)gCounter2.value());

    // After node 3 receive from node 1
    gCounter3 = gCounter3.merge(gCounter1);
    Assert.assertEquals(5, (long)gCounter3.value());

    // After node 3 receive from node 2
    gCounter3 = gCounter3.merge(gCounter2);
    Assert.assertEquals(6, (long)gCounter3.value());
  }
}
