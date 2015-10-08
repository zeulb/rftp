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

  private static int BLOCK_SIZE    = 2048;
  private static int STRING_SIZE   = 256;
  private static int HEADER_SIZE   = 8;
  private static int CHECKSUM_SIZE = 8;
  private static int CONTENT_WITH_NAME_SIZE  = BLOCK_SIZE - CHECKSUM_SIZE - HEADER_SIZE;
  private static int CONTENT_SIZE = BLOCK_SIZE - CHECKSUM_SIZE - HEADER_SIZE - STRING_SIZE;

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

    BufferedReader sourceFileReader = new BufferedReader(new FileReader(sourceFileLocation));

    byte[] dataByte = new byte[BLOCK_SIZE];
    char[] dataChar = new char[BLOCK_SIZE];    

    int sequenceNumber = 0;

    while(true) {
      int length = 0;
      if (sequenceNumber == 0) {
        length = sourceFileReader.read(dataChar, CHECKSUM_SIZE + HEADER_SIZE
                                      + BLOCK_SIZE, CONTENT_SIZE);

      }
      else {
        length = sourceFileReader.read(dataChar, CHECKSUM_SIZE + HEADER_SIZE
                                      + BLOCK_SIZE + STRING_SIZE, CONTENT_WITH_NAME_SIZE);
      }

    
    }

  }
}