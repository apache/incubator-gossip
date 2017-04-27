package org.apache.gossip.transport;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Abstracted safe shutdown constructs from PassiveGossipThread.
 */
public class InterruptibleThread extends Thread {
  private boolean isRunning = false;
  private boolean requestShutdown = false;
  private CountDownLatch awaitLatch = new CountDownLatch(1);
  
  public InterruptibleThread(Runnable runnable, String name) {
    super(runnable, name);
  }
  
  @Override
  public synchronized void start() {
    if (isRunning) {
      throw new RuntimeException("Thread is already running");
    }
    isRunning = true;
    super.start();
  }
  
  public synchronized void requestShutdown() {
    if (!isRunning) return;
    requestShutdown = true;
    synchronized (this) {
      interrupt();
    }
  }
  
  public synchronized boolean shutdown(long millis) throws InterruptedException {
    if (!requestShutdown && isRunning) {
      requestShutdown();
    }
    return awaitLatch.await(millis, TimeUnit.MILLISECONDS);
  }
  
  public synchronized boolean isRunning() {
    return isRunning;
  }

  @Override
  public final void run() {
    super.run();
    awaitLatch.countDown();
    // todo: tiny little race here.
    isRunning = false;
  }
}
