import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.zip.*;
import java.io.*;

class CloseStreamThread extends Thread {
  private OutputStream os = null;
  
  public CloseStreamThread(OutputStream os) {
    super();
    this.os = os;
  }
  
  @Override
  public void run() {
    try {
      os.close();
      System.out.println("Successfully writen to file");
    } catch (Exception e) {
      System.out.println("Failed to close output stream");
    }
  }
}

public class FileReceiver {

  private static final int POSITION_PORT_NUMBER = 0;

  private final int BLOCK_SIZE    = 576;
  private final int STRING_SIZE   = 256;
  private final int SEQUENCE_SIZE = 4;
  private final int ACK_SIZE      = 12;
  private final int CHECKSUM_SIZE = 8;
  private final int HEADER_SIZE   = SEQUENCE_SIZE + CHECKSUM_SIZE;
  private final int CONTENT_SIZE  = BLOCK_SIZE    - HEADER_SIZE;

  DatagramSocket socket;
  SocketAddress senderAddress;
  CRC32 crc;

  public FileReceiver(Integer portNumber) throws Exception {
    crc = new CRC32();
    socket = new DatagramSocket(portNumber);
  }

  private int receivePacket(byte[] data) throws Exception {
    DatagramPacket packet = new DatagramPacket(data, BLOCK_SIZE);
    socket.receive(packet);
    senderAddress = packet.getSocketAddress();
    return packet.getLength();
  }

  public void sendACK(int sequenceNumber) throws Exception {
    ByteBuffer dataBuffer = ByteBuffer.allocate(ACK_SIZE);

    // reserve for checksum
    dataBuffer.putLong(0);

    dataBuffer.putInt(sequenceNumber);

    crc.reset();
    crc.update(dataBuffer.array(), CHECKSUM_SIZE, ACK_SIZE - CHECKSUM_SIZE);

    // add checksum
    dataBuffer.rewind();
    dataBuffer.putLong(crc.getValue());

    // send packet
    DatagramPacket packet = new DatagramPacket(dataBuffer.array(), ACK_SIZE, senderAddress);
    socket.send(packet);
  }

  private void receiveFile() throws Exception {
    OutputStream destinationStream = null;

    byte[] data = new byte[BLOCK_SIZE];
    byte[] container = new byte[BLOCK_SIZE];
    ByteBuffer dataBuffer = ByteBuffer.wrap(data);
    int expectedNumber = 0;

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
      if (checksum == crc.getValue()) {
        int sequenceNumber = dataBuffer.getInt();

        // if sequence number is expected
        if (sequenceNumber == expectedNumber) {
          expectedNumber++;
          System.out.println(sequenceNumber);
          if (sequenceNumber == 0) {
            dataBuffer.get(container, 0, STRING_SIZE);
            // Create an output stream
            String destinationFile = new String(container).trim();
            destinationStream = new BufferedOutputStream(new FileOutputStream(destinationFile));
            Runtime.getRuntime().addShutdownHook(new CloseStreamThread(destinationStream));
          }
          else {
            dataBuffer.get(container, 0, packetLength - HEADER_SIZE);
            // Write to output stream
            destinationStream.write(container, 0, packetLength - HEADER_SIZE);
          }
        }

        sendACK(sequenceNumber);
      }
      else {
        sendACK(-1);
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