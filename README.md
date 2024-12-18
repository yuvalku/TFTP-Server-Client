# TFTP-Server-&-Client

## Motivation
In this project, we developed an extended implementation of the Trivial File Transfer Protocol (TFTP), comprising both server and client components. <br/>
The server architecture adopts the Thread-Per-Client method to effectively handle multiple clients concurrently. <br/>
This project demonstrates our proficiency in communication programming, concurrent coding, and the implementation of complex protocols and encoder-decoders.

## Overview
The TFTP server is a file transfer protocol allowing multiple users to upload and download files from the server and
announce when files are added or deleted from the server. <br/>
The communication between the server and the client(s) is performed using a binary communication protocol, which supports the upload, download, and lookup of files.

## How To Run
The server and client can be run from the same machine, but seperate terminals are needed. <br/>
Both server and client require **maven** to be installed. <br/>
Note that the server needs to be running in order for the client to start successfully. 

### Server
+ Navigate to the **server** directory.
+ Run **mvn compile**
+ Run **mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpServer" -Dexec.args="port"** where 'port' is where the server will be located. <br/>
If **-Dexec.args="port"** is omitted, 'port'=7777 will be the default.

### Client
+ Navigate to the **client** directory.
+ Run **mvn compile**
+ Run **mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpClient" -Dexec.args="ip port"** where 'ip' and 'port' are the relevant ip adress and port of the server. <br/>
If **-Dexec.args="ip port"** is omitted, 'ip'=localhost and 'port'=7777 will be the default.

## Client Behavior

### Keyboard thread Commands
In this section, we describe the commands that we can type to our terminal and the resulting behavior.
#### LOGRQ
+ Login User to the server
+ Format: LOGRQ <username\>
+ Result: Sending a LOGRQ packet to the server with the <Username\> and waiting for ACK with block
number 0 or ERROR packet to be received in the Listening thread.
#### DELRQ
+ Delete File from the server.
+ Format: DELRQ <Filename\>
+ Result: Sending a DELRQ packet to the server with the <Filename\> and waiting for ACK with block number
0 or ERROR packet to be received in the Listening thread.
#### RRQ
+ Download file from the server Files folder to current working directory.
+ Format: RRQ <Filename\>
+ Result: Creating a file in the current working directory and then sending a RRQ packet to the server with
the <Filename\> and waiting for file to complete the transfer (Server sending DATA Packets) or ERROR
packet to be received in the Listening thread.
#### WRQ
+ Upload File from current working directory to the server.
+ Format: WRQ <Filename\>
+ Result: Check if the file exists, then send a WRQ packet and wait for ACK or ERROR packet to be received in
the Listening thread. If received ACK, the file begins to transfer.
#### DIRQ
+ List all the file names that are in Files folder in the server.
+ Format: DIRQ
+ Result: Sending a DIRQ packet to the server with waiting for the filenames to complete the transfer (server
sending DATA pakets) or an ERROR packet to be received in the Listening thread.
#### DISC
+ Disconnect (Server remove user from Logged-in list) from the server and close the program.
+ Format: DISC
+  Result: Check if User is logged in. <br/>
If User is logged in, sends DISC packet and waits for ACK with block number 0 or ERROR packet to
be received in the Listening thread. Then closes the socket and exits the client program. <br/>
If User is not logged in, closes socket and exits the client program.