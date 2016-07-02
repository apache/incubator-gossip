package org.apache.gossip.manager;

import java.nio.ByteBuffer;

public class UdpUtil {

  public static int readPacketLengthFromBuffer(byte [] buffer){
    int packetLength = 0;
    for (int i = 0; i < 4; i++) {
      int shift = (4 - 1 - i) * 8;
      packetLength += (buffer[i] & 0x000000FF) << shift;
    }
    return packetLength;
  }
  
  public static byte[] createBuffer(int packetLength, byte[] jsonBytes) {
    byte[] lengthBytes = new byte[4];
    lengthBytes[0] = (byte) (packetLength >> 24);
    lengthBytes[1] = (byte) ((packetLength << 8) >> 24);
    lengthBytes[2] = (byte) ((packetLength << 16) >> 24);
    lengthBytes[3] = (byte) ((packetLength << 24) >> 24);
    ByteBuffer byteBuffer = ByteBuffer.allocate(4 + jsonBytes.length);
    byteBuffer.put(lengthBytes);
    byteBuffer.put(jsonBytes);
    byte[] buf = byteBuffer.array();
    return buf;
  }
}
