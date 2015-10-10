import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.zip.*;
import java.io.*;

public class FileReceiver {

  private static final int POSITION_PORT_NUMBER = 0;
  private static final int BLOCK_SIZE    = 576;
  private static final int STRING_SIZE   = 256;
  private static final int HEADER_SIZE   = 8;
  private static final int CHECKSUM_SIZE = 8;
  private static int CONTENT_WITH_NAME_SIZE  = BLOCK_SIZE - CHECKSUM_SIZE - HEADER_SIZE - STRING_SIZE;
  private static int CONTENT_SIZE = BLOCK_SIZE - CHECKSUM_SIZE - HEADER_SIZE;

  public static void main(String args[]) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: FileReceiver <port>");
      System.exit(-1);
    }

    Integer portNumber = Integer.parseInt(args[POSITION_PORT_NUMBER]);

    System.out.println(portNumber);

    
    CRC32 crc = new CRC32();
    DatagramSocket sk = new DatagramSocket(portNumber);

    byte[] data = new byte[BLOCK_SIZE];
    ByteBuffer dataBuffer = ByteBuffer.wrap(data);
    OutputStream destinationFileStream = null;


    int co = 0;
    while(true) {
      dataBuffer.clear();
      DatagramPacket pkt = new DatagramPacket(data, BLOCK_SIZE);

      co++;
      //System.out.println("makasi " + co);

      sk.receive(pkt);
      int pktLen = pkt.getLength();
      System.out.println(pkt.getLength());
      
      
      if (pkt.getLength() < CHECKSUM_SIZE) {
        System.out.println("Pkt too short");
        assert(false);
        continue;
      }
      
      long chksum = dataBuffer.getLong();

      crc.reset();
      crc.update(data, 8, pktLen-8);

      if (crc.getValue() != chksum)
      {
        System.out.println("Pkt corrupt");
      }
      else
      {
        long sequenceNumber = dataBuffer.getLong()+1;
        //System.out.println("Pkt " + sequenceNumber);
        assert(sequenceNumber == co);
        
        
        
        if (sequenceNumber == 1) {
          byte[] name = new byte[STRING_SIZE];
          byte[] lol = new byte[CONTENT_WITH_NAME_SIZE];
          dataBuffer.get(name, 0, STRING_SIZE);
          int remain = pktLen-16-STRING_SIZE;
          dataBuffer.get(lol);
          String destinationFileLocation = new String(name).trim();
          System.out.println(destinationFileLocation);
          destinationFileStream = new BufferedOutputStream(new FileOutputStream(destinationFileLocation));
          destinationFileStream.write(lol, 0, remain);
          System.out.println("success");
        }
        else {
          byte[] lol = new byte[CONTENT_SIZE];
          int remain = pktLen-16;
          dataBuffer.get(lol, 0, CONTENT_SIZE);
          destinationFileStream.write(lol, 0, remain);
          //System.out.println(new String(lol));
          System.out.println(remain);
          System.out.println(CONTENT_SIZE);
          if (remain != CONTENT_SIZE) {
            destinationFileStream.close();
            break;
          }
        }
      }
      dataBuffer.rewind();
    }

    //destinationFileStream.close();   
  }
}