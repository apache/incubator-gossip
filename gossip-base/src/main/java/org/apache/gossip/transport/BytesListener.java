package org.apache.gossip.transport;

import java.io.IOException;

public interface BytesListener {
  void bytesReceived(byte[] buf) throws IOException;
}
