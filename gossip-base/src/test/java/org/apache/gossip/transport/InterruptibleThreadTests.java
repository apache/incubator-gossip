package org.apache.gossip.transport;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptibleThreadTests {
  
  @Test
  public void testSimpleShutdown() throws Exception {
    InterruptibleThread thread = new InterruptibleThread(() -> uninteruptibleSleep(100), "100ms");
    thread.start();
    Assert.assertTrue(thread.shutdown(200));
  }
  
  @Test
  public void testShutdownTakesTooLong() throws Exception {
    InterruptibleThread thread = new InterruptibleThread(() -> uninteruptibleSleep(100), "100ms");
    thread.start();
    Assert.assertFalse(thread.shutdown(10));
  }
  
  @Test
  public void multipleWaiters() throws Exception {
    final AtomicBoolean unexpectedException = new AtomicBoolean(false);
    final InterruptibleThread thread = new InterruptibleThread(() -> uninteruptibleSleep(100), "100ms");
    thread.start();
    final int waiters = 10;
    final CountDownLatch latch = new CountDownLatch(waiters);
    for (int i = 0; i < waiters; i++) {
      new Thread() {
        public void run() {
          try {
            if (thread.shutdown(200)) {
              latch.countDown();
            }
          }
          catch (Exception ex) {
            unexpectedException.set(true);
          }
        }
      }.start();
    }
    Assert.assertTrue("" + latch.getCount(), latch.await(250, TimeUnit.MILLISECONDS));
  }
  
  @Test
  public void testLateWaiter() throws Exception {
    InterruptibleThread thread = new InterruptibleThread(() -> uninteruptibleSleep(1), "1ms");
    thread.start();
    Assert.assertTrue(thread.shutdown(100));
    Assert.assertFalse(thread.isRunning());
    uninteruptibleSleep(100); // gives thread some time to die.
    Assert.assertFalse(thread.isAlive());
    Assert.assertTrue(thread.shutdown(100)); // this is the late waiter.
  }
  
  private static void uninteruptibleSleep(long ms) {
    long start = System.currentTimeMillis();
    long delta = 0;
    while (delta < ms) {
      try {
        Thread.sleep(ms - delta);
      } catch (InterruptedException ignore) { }
      delta = System.currentTimeMillis() - start;
    }
  }
}
