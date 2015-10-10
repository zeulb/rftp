import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.zip.*;
import java.io.*;

public class FileReceiver {

  private static final int POSITION_PORT_NUMBER = 0;

  private final int BLOCK_SIZE              = 576;
  private final int STRING_SIZE             = 256;
  private final int SEQUENCE_SIZE           = 8;
  private final int CHECKSUM_SIZE           = 8;
  private final int HEADER_SIZE             = SEQUENCE_SIZE + CHECKSUM_SIZE;
  private final int CONTENT_SIZE            = BLOCK_SIZE    - HEADER_SIZE;

  DatagramSocket socket;
  CRC32 crc;

  public FileReceiver(Integer portNumber) throws Exception {
    crc = new CRC32();
    socket = new DatagramSocket(portNumber);
  }

  private int receivePacket(byte[] data) throws Exception {
    DatagramPacket packet = new DatagramPacket(data, BLOCK_SIZE);
    socket.receive(packet);
    return packet.getLength();
  }

  private void receiveFile() throws Exception {
    OutputStream destinationStream = null;

    byte[] data = new byte[BLOCK_SIZE];
    byte[] container = new byte[BLOCK_SIZE];
    ByteBuffer dataBuffer = ByteBuffer.wrap(data);

    while(true) {
      dataBuffer.clear();
      int packetLength = receivePacket(data);

      if (packetLength < CHECKSUM_SIZE) {
        throw new Exception("Packet too short");
      }

      long checksum = dataBuffer.getLong();

      // compare checksum
      crc.reset();
      crc.update(data, CHECKSUM_SIZE, packetLength - CHECKSUM_SIZE);

      if (checksum != crc.getValue()) {
        throw new Exception("Packet corrupt");
      }
      else {
        long sequenceNumber = dataBuffer.getLong();

        if (sequenceNumber == 0) {
          dataBuffer.get(container, 0, STRING_SIZE);

          // Create an output stream
          String destinationFile = new String(container).trim();
          destinationStream = new BufferedOutputStream(new FileOutputStream(destinationFile));
        }
        else {
          dataBuffer.get(container, 0, packetLength - HEADER_SIZE);

          destinationStream.write(container, 0, packetLength - HEADER_SIZE);
        }
      }
    }
  }

  public static void main(String args[]) throws Exception {

    if (args.length != 1) {
      System.err.println("Usage: FileReceiver <port>");
      System.exit(-1);
    }

    Integer portNumber = Integer.parseInt(args[POSITION_PORT_NUMBER]);

    FileReceiver fileReceiver = new FileReceiver(portNumber);

    fileReceiver.receiveFile();
  }
}