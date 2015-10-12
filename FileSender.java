import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.zip.*;
import java.io.*;

public class FileSender {

  private static final int POSITION_HOST_NAME = 0;
  private static final int POSITION_PORT_NUMBER = 1;
  private static final int POSITION_SOURCE_FILE_LOCATION = 2;
  private static final int POSITION_DESTINATION_FILE_LOCATION = 3;

  private final int BUCKET_SIZE   = 90;
  private final int BLOCK_SIZE    = 1000;
  private final int MAX_SEND      = 6;
  private final int STRING_SIZE   = 256;
  private final int SEQUENCE_SIZE = 4;
  private final int ACK_SIZE      = 13;
  private final int CHECKSUM_SIZE = 8;
  private final int HEADER_SIZE   = SEQUENCE_SIZE + CHECKSUM_SIZE;
  private final int CONTENT_SIZE  = BLOCK_SIZE    - HEADER_SIZE;
  private final int TIMEOUT = 1;

  InetSocketAddress receiverAddress;
  InputStream sourceStream;
  CRC32 crc;
  DatagramSocket socket;
  int sequenceNumber;
  int pendingNumber;
  private boolean[] ackTable;
  private byte[][] dataTable;
  private int[] lengthTable;
  boolean sourceEmpty = false;

  public FileSender(String hostName, Integer portNumber, String sourceFile) throws Exception {
    receiverAddress = new InetSocketAddress(hostName, portNumber);
    sourceStream = new BufferedInputStream(new FileInputStream(sourceFile));
    crc = new CRC32();
    socket = new DatagramSocket();
    socket.setSoTimeout(TIMEOUT);
    ackTable = new boolean[BUCKET_SIZE];
    dataTable = new byte[BUCKET_SIZE][BLOCK_SIZE];
    lengthTable = new int[BUCKET_SIZE];
    sequenceNumber = pendingNumber = 0;
    sourceEmpty = false;
  }

  public String getNormalizedString(String fileName) {
    StringBuffer sb = new StringBuffer(fileName);
    while(sb.length() < STRING_SIZE) {
      sb.append(' ');
    }
    return sb.toString();
  }

  private void markDone(int successNumber) throws Exception {
    if (successNumber < pendingNumber) return;
    //System.out.println("ack " + successNumber);
    ackTable[successNumber%BUCKET_SIZE] = false;
   // System.out.println("unset " + successNumber + " " + (successNumber%BUCKET_SIZE));
    
    byte[] data = new byte[BLOCK_SIZE];
    while(pendingNumber%BUCKET_SIZE != sequenceNumber%BUCKET_SIZE && !sourceEmpty) {
      int length = sourceStream.read(data, 0, CONTENT_SIZE);
      if (length == -1) {
        sourceEmpty = true;
        break;
      }
      sendPacket(sequenceNumber++, data, length);
    }

    while(ackTable[pendingNumber%BUCKET_SIZE] == false && pendingNumber < sequenceNumber) {
     // System.out.println("oyes " + pendingNumber);
      pendingNumber++;
      
      if (sourceEmpty) continue;

      int length = sourceStream.read(data, 0, CONTENT_SIZE);
      if (length == -1) {
        sourceEmpty = true;
        continue;
      }
      sendPacket(sequenceNumber++, data, length);
    }


  }

  private boolean receiveACK() throws Exception {
    //System.out.println("called " + pendingNumber + " " + sequenceNumber);
    ByteBuffer dataBuffer = ByteBuffer.allocate(ACK_SIZE);
    DatagramPacket packet = new DatagramPacket(dataBuffer.array(), ACK_SIZE);
    try {
      socket.receive(packet);
    } catch (Exception e) {
      return false;
    }
    long checksum = dataBuffer.getLong();

    crc.reset();
    crc.update(dataBuffer.array(), CHECKSUM_SIZE, ACK_SIZE - CHECKSUM_SIZE);

    if (checksum == crc.getValue()) {
      int packetNumber = dataBuffer.getInt();
      byte value = dataBuffer.get();
      if (value == (byte)0) {
        if (sequenceNumber > packetNumber && packetNumber >= pendingNumber) {
          sendBlock(packetNumber%BUCKET_SIZE);
        }
      }
      else {
        markDone(packetNumber);
      }
      return true; 
    }
    return false;
  }

  public void sendBlock(int blockId) throws Exception {
    int length = lengthTable[blockId];
    DatagramPacket packet = new DatagramPacket(dataTable[blockId], HEADER_SIZE + length, receiverAddress);
    socket.send(packet);
  }

  public void sendPacket(int sequenceNumber, byte[] data, int length) throws Exception {
    //System.out.println("Packet " + sequenceNumber + " " + length);

    ByteBuffer dataBuffer = ByteBuffer.allocate(HEADER_SIZE + length);

    // reserve for checksum
    dataBuffer.putLong(0);

    // add sequence number
    dataBuffer.putInt(sequenceNumber);

    // add content
    dataBuffer.put(data, 0, length);

    crc.reset();
    crc.update(dataBuffer.array(), CHECKSUM_SIZE, HEADER_SIZE + length - CHECKSUM_SIZE);

    // add checksum
    dataBuffer.rewind();
    dataBuffer.putLong(crc.getValue());

    //System.out.println(sequenceNumber + " " + (HEADER_SIZE + length));
    
    // save packet
    int blockId = sequenceNumber%BUCKET_SIZE;
    dataTable[blockId]   = dataBuffer.array().clone();
    lengthTable[blockId] = length;
    ackTable[blockId]    = true; 
  //  System.out.println("set " + sequenceNumber + " " + blockId);

  //  System.out.println("send " + sequenceNumber);
    sendBlock(blockId);
  }

  private void sendFile(String destinationFile) throws Exception {
    // send string name packet
    sendPacket(sequenceNumber++, getNormalizedString(destinationFile).getBytes(), 256);

    int countFail = 0;
    while(pendingNumber < sequenceNumber) {
      if (receiveACK()) {
        countFail = 0;
        continue;
      }

      countFail++;
      int send = 0;
      for(int i = pendingNumber; i < sequenceNumber; i++) {
        if (ackTable[i%BUCKET_SIZE]) {
    //      System.out.println("send " + i);
          send++;
          sendBlock(i%BUCKET_SIZE);
        }
        if (send == MAX_SEND) break;
      }
      countFail = 0;
    }
  }

  public static void main(String args[]) throws Exception {

    if (args.length != 4) {
      System.err.println("Usage: FileSender <host> <port> <source_file> <destination_file>");
      System.exit(-1);
    }

    String hostName    = args[POSITION_HOST_NAME];
    Integer portNumber = Integer.parseInt(args[POSITION_PORT_NUMBER]);

    String sourceFileLocation      = args[POSITION_SOURCE_FILE_LOCATION];
    String destinationFileLocation = args[POSITION_DESTINATION_FILE_LOCATION];

    FileSender fileSender = new FileSender(hostName, portNumber, sourceFileLocation);
    fileSender.sendFile(destinationFileLocation);

  }
}