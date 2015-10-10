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

  private final int BLOCK_SIZE              = 576;
  private final int STRING_SIZE             = 256;
  private final int SEQUENCE_SIZE           = 8;
  private final int CHECKSUM_SIZE           = 8;
  private final int HEADER_SIZE             = SEQUENCE_SIZE + CHECKSUM_SIZE;
  private final int CONTENT_SIZE            = BLOCK_SIZE    - HEADER_SIZE;

  InetSocketAddress receiverAddress;
  CRC32 crc;
  DatagramSocket socket;

  public FileSender(String hostName, Integer portNumber) throws Exception {
    receiverAddress = new InetSocketAddress(hostName, portNumber);
    crc = new CRC32();
    socket = new DatagramSocket();
  }

  public String getNormalizedString(String fileName) {
    StringBuffer sb = new StringBuffer(fileName);
    while(sb.length() < STRING_SIZE) {
      sb.append(' ');
    }
    return sb.toString();
  }

  public void sendPacket(long sequenceNumber, byte[] data, int length) throws Exception {
    ByteBuffer dataBuffer = ByteBuffer.allocate(HEADER_SIZE + length);

    // reserve for checksum
    dataBuffer.putLong(0);

    // add sequence number
    dataBuffer.putLong(sequenceNumber);

    // add content
    dataBuffer.put(data, 0, length);

    crc.reset();
    crc.update(dataBuffer.array(), CHECKSUM_SIZE, HEADER_SIZE + length - CHECKSUM_SIZE);

    // add checksum
    dataBuffer.rewind();
    dataBuffer.putLong(crc.getValue());

    // send packet
    DatagramPacket packet = new DatagramPacket(dataBuffer.array(), HEADER_SIZE + length, receiverAddress);
    socket.send(packet);

  }

  private void sendFile(String sourceFile, String destinationFile) throws Exception {

    InputStream sourceStream = new BufferedInputStream(new FileInputStream(sourceFile));

    byte[] data = new byte[BLOCK_SIZE];
    ByteBuffer b = ByteBuffer.wrap(data);

    long sequenceNumber = 0;

    sendPacket(sequenceNumber++, getNormalizedString(destinationFile).getBytes(), 256);

    // send packet
    int length;
    while((length = sourceStream.read(data, 0, CONTENT_SIZE)) > 0) {
      sendPacket(sequenceNumber++, data, length);
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

    FileSender fileSender = new FileSender(hostName, portNumber);
    fileSender.sendFile(sourceFileLocation, destinationFileLocation);

  }
}