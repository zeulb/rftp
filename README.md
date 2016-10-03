# Reliable Fife Transfer Protocol

Implementation of a reliable file transfer protocol over UDP.

## Usage
`java FileSender localhost 9000 ../large.mp4 big.mp4`


## Feature
- Can successfully transfer a file from sender to receiver in the presence of packets being reordered.
- Can successfully transfer a file from sender to receiver in the presence of packet corruption, packet loss and reordering