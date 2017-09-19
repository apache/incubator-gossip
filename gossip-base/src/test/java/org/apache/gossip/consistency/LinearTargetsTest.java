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

import org.apache.gossip.LocalMember;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LinearTargetsTest {

  @Test
  public void basicTest() {
    LinearTargets q = new LinearTargets(1);
    LocalMember me = new LocalMember() { };
    me.setId("1");
    Assert.assertEquals(me.getId(), q.generateTargets("b", me, new ArrayList<>(), new ArrayList<>()).get(0).getId());
  }

  @Test
  public void simpleTest() {
    LinearTargets q = new LinearTargets(3);
    LocalMember me = new LocalMember() {};
    me.setId("10");
    Assert.assertEquals(3, q.generateTargets("b", me, make10(), new ArrayList<>()).size());
    List<LocalMember> x = q.generateTargets("b", me, make10(), new ArrayList<>());
    Assert.assertEquals("9", x.get(0).getId());
    Assert.assertEquals("0", x.get(1).getId());
    Assert.assertEquals("1", x.get(2).getId());
  }

  public List<LocalMember> make10() {
    List<LocalMember> a = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      LocalMember me = new LocalMember() {
      };
      me.setId(i + "");
      a.add(me);
    }
    return a;
  }
}
