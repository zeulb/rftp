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
      System.out.println("Successfully written to file");
    } catch (Exception e) {
      System.out.println("Failed to close output stream");
    }
  }
}

public class FileReceiver {

  private static final int POSITION_PORT_NUMBER = 0;

  private final int BUCKET_SIZE   = 50;
  private final int BLOCK_SIZE    = 1000;
  private final int STRING_SIZE   = 256;
  private final int SEQUENCE_SIZE = 4;
  private final int ACK_SIZE      = 12;
  private final int CHECKSUM_SIZE = 8;
  private final int HEADER_SIZE   = SEQUENCE_SIZE + CHECKSUM_SIZE;
  private final int CONTENT_SIZE  = BLOCK_SIZE    - HEADER_SIZE;

  DatagramSocket socket;
  SocketAddress senderAddress;
  private byte[][] dataTable;
  private int[] lengthTable;
  private boolean[] filledTable;
  private int pendingNumber;
  CRC32 crc;

  public FileReceiver(Integer portNumber) throws Exception {
    crc = new CRC32();
    socket = new DatagramSocket(portNumber);
    dataTable = new byte[BUCKET_SIZE][BLOCK_SIZE];
    lengthTable = new int[BUCKET_SIZE];
    filledTable = new boolean[BUCKET_SIZE];
    pendingNumber = 0;
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
 //   System.out.println("send ACK " + sequenceNumber);
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
        continue;
      }

      long checksum = dataBuffer.getLong();

      // compare checksum
      crc.reset();
      crc.update(data, CHECKSUM_SIZE, packetLength - CHECKSUM_SIZE);
      if (checksum == crc.getValue()) {
        int sequenceNumber = dataBuffer.getInt();
    //    System.out.println("receive " + sequenceNumber + " " + pendingNumber);
        int blockId = sequenceNumber%BUCKET_SIZE;
        sendACK(sequenceNumber);
        if (sequenceNumber < pendingNumber || filledTable[blockId]) continue;

        filledTable[blockId] = true;
        dataBuffer.get(container, 0, packetLength - HEADER_SIZE);
        dataTable[blockId] = container.clone();
        lengthTable[blockId] = packetLength - HEADER_SIZE;        
      }

      while (filledTable[pendingNumber%BUCKET_SIZE]) {
     //   System.out.println("process " + pendingNumber);
        if (pendingNumber == 0) {
          // Create an output stream
          String destinationFile = new String(dataTable[0]).trim();
          destinationStream = new BufferedOutputStream(new FileOutputStream(destinationFile));
          Runtime.getRuntime().addShutdownHook(new CloseStreamThread(destinationStream));
        }
        else {
          // Write to output stream
          destinationStream.write(dataTable[pendingNumber%BUCKET_SIZE], 0, lengthTable[pendingNumber%BUCKET_SIZE]);
        }
        filledTable[pendingNumber%BUCKET_SIZE] = false;
        pendingNumber++;
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