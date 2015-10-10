import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.zip.*;
import java.io.*;

public class FileSender {

  private static int POSITION_HOST_NAME = 0;
  private static int POSITION_PORT_NUMBER = 1;
  private static int POSITION_SOURCE_FILE_LOCATION = 2;
  private static int POSITION_DESTINATION_FILE_LOCATION = 3;

  private static int BLOCK_SIZE    = 576;
  private static int STRING_SIZE   = 256;
  private static int HEADER_SIZE   = 8;
  private static int CHECKSUM_SIZE = 8;
  private static int CONTENT_WITH_NAME_SIZE  = BLOCK_SIZE - CHECKSUM_SIZE - HEADER_SIZE - STRING_SIZE;
  private static int CONTENT_SIZE = BLOCK_SIZE - CHECKSUM_SIZE - HEADER_SIZE;

  public static void main(String args[]) throws Exception {
     
    if (args.length != 4) {
      System.err.println("Usage: FileSender <host> <port> <source_file> <destination_file>");
      System.exit(-1);
    }

    String hostName = args[POSITION_HOST_NAME];
    Integer portNumber = Integer.parseInt(args[POSITION_PORT_NUMBER]);
    String sourceFileLocation = args[POSITION_SOURCE_FILE_LOCATION];
    String destinationFileLocation = args[POSITION_DESTINATION_FILE_LOCATION];

    InetSocketAddress address = new InetSocketAddress(hostName, portNumber);
    System.out.println(address);

    

    InputStream sourceFileStream = new BufferedInputStream(new FileInputStream(sourceFileLocation));

    

    long sequenceNumber = 0;

    while(destinationFileLocation.length() < 256) {
      destinationFileLocation += " ";
    }

    CRC32 crc = new CRC32();
      DatagramSocket sk = new DatagramSocket();
      DatagramPacket pkt;

    byte[] data = new byte[BLOCK_SIZE];
    ByteBuffer b = ByteBuffer.wrap(data);

    while(true) {
      
      int length;
      if (sequenceNumber == 0) {
        length = sourceFileStream.read(data, 0, CONTENT_WITH_NAME_SIZE);
      }
      else {
        length = sourceFileStream.read(data, 0, CONTENT_SIZE);
      }
      if (length == -1) break;

      int totalLength = length;

      if (sequenceNumber == 0) {
        totalLength += STRING_SIZE;
      }

      totalLength += CHECKSUM_SIZE + HEADER_SIZE;

      ByteBuffer dataBuffer = ByteBuffer.allocate(totalLength);

      // reserve space for checksum
      dataBuffer.putLong(0);
      System.out.println(sequenceNumber);
      dataBuffer.putLong(sequenceNumber);

      // add destination file name
      if (sequenceNumber == 0) {
        dataBuffer.put(destinationFileLocation.getBytes());
      }
    
      dataBuffer.put(data, 0, length);

      crc.reset();
      crc.update(dataBuffer.array(), CHECKSUM_SIZE, totalLength-CHECKSUM_SIZE);
      long chksum = crc.getValue();
      dataBuffer.rewind();
      dataBuffer.putLong(chksum);
      System.out.println(totalLength);
      pkt = new DatagramPacket(dataBuffer.array(), totalLength, address);
      sk.send(pkt);

      sequenceNumber++;
    }

  }
}