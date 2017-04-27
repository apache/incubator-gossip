package org.apache.gossip.manager;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.apache.gossip.model.Base;
import org.apache.gossip.model.Response;
import org.apache.gossip.protocol.ProtocolManager;
import org.apache.gossip.transport.TransportManager;
import org.apache.gossip.udp.Trackable;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MessagingManager {
  public static final Logger LOGGER = Logger.getLogger(GossipCore.class);
  
  private ConcurrentHashMap<String, LatchAndBase> requests;
  private final TransportManager transport;
  private final ProtocolManager protocol;
  private final Clock clock;
  
  // metrics.
  private final Meter messageSerdeException;
  private final Meter tranmissionException;
  private final Meter tranmissionSuccess;
  
  private static class LatchAndBase {
    private final CountDownLatch latch;
    private volatile Base base;
    
    LatchAndBase(){
      latch = new CountDownLatch(1);
    }
  }
  
  public MessagingManager(TransportManager transport, ProtocolManager protocol, Clock clock, MetricRegistry metrics) {
    this.transport = transport;
    this.protocol = protocol;
    this.clock = clock;
    requests = new ConcurrentHashMap<>();
    
    metrics.register(GossipCoreConstants.REQUEST_SIZE, (Gauge<Integer>)() ->  requests.size());
    messageSerdeException = metrics.meter(GossipCoreConstants.MESSAGE_SERDE_EXCEPTION);
    tranmissionException = metrics.meter(GossipCoreConstants.MESSAGE_TRANSMISSION_EXCEPTION);
    tranmissionSuccess = metrics.meter(GossipCoreConstants.MESSAGE_TRANSMISSION_SUCCESS);
  }
  
  public void start() {
    transport.startEndpoint();
  }
  
  public void shutdown() {
    transport.shutdown();
  }
  
  public long getNanoTime() { return clock.nanoTime(); }
  
  public void handleResponse(String k, Base v) {
    LatchAndBase latch = requests.get(k);
    latch.base = v;
    latch.latch.countDown();
  }
  
  /**
   * Sends a blocking message.
   * todo: move functionality to TransportManager layer.
   * @param message
   * @param uri
   * @throws RuntimeException if data can not be serialized or in transmission error
   */
  private void sendInternal(Base message, URI uri) {
    byte[] json_bytes;
    try {
      json_bytes = protocol.write(message);
    } catch (IOException e) {
      messageSerdeException.mark();
      throw new RuntimeException(e);
    }
    try {
      transport.send(uri, json_bytes);
      tranmissionSuccess.mark();
    } catch (IOException e) {
      tranmissionException.mark();
      throw new RuntimeException(e);
    }
  }
  
  public Response send(Base message, URI uri){
    if (LOGGER.isDebugEnabled()){
      LOGGER.debug("Sending " + message);
      LOGGER.debug("Current request queue " + requests);
    }

    final Trackable t;
    LatchAndBase latchAndBase = null;
    if (message instanceof Trackable){
      t = (Trackable) message;
      latchAndBase = new LatchAndBase();
      requests.put(t.getUuid() + "/" + t.getUriFrom(), latchAndBase);
    } else {
      t = null;
    }
    sendInternal(message, uri);
    if (latchAndBase == null){
      return null;
    } 
    
    try {
      boolean complete = latchAndBase.latch.await(1, TimeUnit.SECONDS);
      if (complete){
        return (Response) latchAndBase.base;
      } else{
        return null;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      if (latchAndBase != null){
        requests.remove(t.getUuid() + "/" + t.getUriFrom());
      }
    }
  }

  /**
   * Sends a message across the network while blocking. Catches and ignores IOException in transmission. Used
   * when the protocol for the message is not to wait for a response
   * @param message the message to send
   * @param u the uri to send it to
   */
  public void sendOneWay(Base message, URI u) {
    try {
      sendInternal(message, u);
    } catch (RuntimeException ex) {
      LOGGER.debug("Send one way failed", ex);
    }
  }
}
