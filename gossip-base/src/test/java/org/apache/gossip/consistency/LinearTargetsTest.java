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
