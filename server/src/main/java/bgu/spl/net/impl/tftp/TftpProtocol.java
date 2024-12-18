package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

// Added
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;


// for keeping up with which bytes were already sent by now
class sendingFile{

    private int curr;
    private short blockNum;
    private byte[] file;
    private boolean lastNeeded;

    public sendingFile(byte[] file){
        this.curr = 0;
        this.blockNum = 0;
        this.file = file;
        lastNeeded = false;
    }

    public byte[] generatePacket(){
        int size = file.length - curr;

        if (size > 512)
            size = 512;
        
        else if(size == 0){
            lastNeeded = false;
        }
        blockNum++;

        short packetSize = (short)size;
        byte[] output = new byte[size + 6];
        
        output[0] = (byte)0;
        output[1] = (byte)3;
        output[2] = (byte)((packetSize >> 8) & 0xFF);
        output[3] = (byte)(packetSize & 0xFF);
        output[4] = (byte)((blockNum >> 8) & 0xFF);
        output[5] = (byte)(blockNum & 0xFF);
        

        for (int i = 6; i < size + 6; i++){
            output[i] = file[curr];
            curr++;
        }

        if(curr == file.length & size == 512){
            lastNeeded = true;
        }
        
        return output;
    }

    public boolean isFinished(){
        return (curr == file.length && !lastNeeded);
    }
}

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private boolean shouldTerminate;
    private int connectionId;
    private Connections<byte[]> connections;
    private String username;
    private sendingFile toSend;
    private String uploadingFileName;
    private Queue<byte[]> uploadingFile;
    private int UFsize;
    private final String directory = "./Flies";

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        
        this.shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        holder.ids_login.put(connectionId, false);
        toSend = null;
        uploadingFile = new LinkedList<>();
        UFsize = 0;
    }

    @Override
    public void process(byte[] message) {
        
        short opCode = (short)(((short)message[0]) << 8 | (short)(message[1]) & 0x00ff);

        if (opCode != 7 && !holder.ids_login.get(connectionId)) {
            connections.send(connectionId, createError((byte)6, "User not logged in"));
        }

        // RRQ
        else if (opCode == 1){

            // extract file name and check if exists
            String fileName = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
            byte[] file = extractFile(directory + "/" + fileName);

            // check if file exists
            if (!fileExists(directory + "/" + fileName)){
                connections.send(connectionId, createError((byte)1, "File not found"));
            }

            // check if file is accessible
            else if (!isAccessible(directory + "/" + fileName) || file == null){
                connections.send(connectionId, createError((byte)2, "Access violation"));
            }
            
            // if file exists, start sending it
            else{
                toSend = new sendingFile(file);
                connections.send(connectionId, toSend.generatePacket());
            }
        }

        // WRQ
        else if (opCode == 2){

            // extract file name and check if exists
            uploadingFileName = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
            if (fileExists(directory + "/" + uploadingFileName)){
                connections.send(connectionId, createError((byte)5, "File already exists"));
            }

            // if doesn't exists start uploading
            else{
                connections.send(connectionId, ack((byte)0,(byte)0));
            }

        }

        // DATA
        else if (opCode == 3){
            // add data packet to queue
            uploadingFile.add(message);

            // send acknowledgment
            connections.send(connectionId, ack(message[4], message[5]));

            // check data's size
            short packetSize = (short)(((short)message[2]) << 8 | (short)(message[3]) & 0x00ff);
            UFsize += packetSize;

            // if packetSize < 512, this is the last packet
            if (packetSize < 512){
                byte[] file = buildFileBytes(uploadingFile);
                if (addNewFile(uploadingFileName, file)){
                    broadCast(uploadingFileName, true);
                }
                else{
                    connections.send(connectionId, createError((byte)3,"Disk full or allocation exceeded"));
                }
            }
        }

        // ACK
        else if (opCode == 4){
            
            if (!toSend.isFinished()){
                connections.send(connectionId, toSend.generatePacket());
            }

        }

        // DIRQ
        else if (opCode == 6){
            
            Vector<String> vec = new Vector<>();

            // extract files from directory
            File _directory = new File(directory);
            File[] files = _directory.listFiles();

            // check if directory is empty
            if (files == null || files.length == 0){
                connections.send(connectionId, createError((byte)0, "Directory is empty"));
            }
            else {

                // insert all file names to the vector
                for (int i = 0; i < files.length; i++)
                    vec.add(files[i].getName());

                // convert to an array of bytes and start sending to the client
                byte[] fileNames = getFileNames(vec);
                toSend = new sendingFile(fileNames);
                connections.send(connectionId, toSend.generatePacket());
            }
        }

        // LOGRQ
        else if (opCode == 7){

            // extract username from bytes
            username = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);

            // if username is taken
            if (holder.usernames.contains(username))
                connections.send(connectionId, createError((byte)7, "User already logged in"));
            
            // if client already logged in
            else if (holder.ids_login.get(connectionId))
                connections.send(connectionId, createError((byte)0, "Current client already logged in"));
            
            //if there is no active client with this username, register it
            else {
                holder.ids_login.put(connectionId, true);
                holder.usernames.add(username);
                connections.send(connectionId, ack((byte)0,(byte)0));
            }

        }

        // DELRQ
        else if (opCode == 8){

            // extract fileName from bytes
            String fileName = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);

            // if file exists
            if (fileExists(directory + "/" + fileName)){
                File tempFile = new File(directory + "/" + fileName);
                tempFile.delete(); 
                connections.send(connectionId, ack((byte)0,(byte)0));
                broadCast(fileName, false);
            }

            // if file doesn't exists
            else{
                connections.send(connectionId, createError((byte)1, "File not found"));
            }
        }

        // Disc
        else{
            
            // if logged in send ack
            if (holder.ids_login.get(connectionId)){
                connections.send(connectionId, ack((byte)0,(byte)0));
            }

            // if not logged in send error
            else
                connections.send(connectionId, createError((byte)6, "User no logged in"));

            // remove from ids_login and from connections
            holder.ids_login.remove(connectionId);
            holder.usernames.remove(username);
            connections.disconnect(connectionId);
            shouldTerminate = true;
        }

    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 

    // Added

    public boolean isLoggedIn(){
        return holder.ids_login.get(connectionId);
    }

    public String getUsername(){
        return username;
    }

    // create the relevant bytes array for the given error
    private static byte[] createError(byte errorCode, String message){
        byte[] errorMessage = message.getBytes();
        byte[] opC = {0, 5, 0, errorCode};
        byte[] output = new byte[errorMessage.length + opC.length];
        for (int i = 0; i < output.length; i++){
            if (i < 4)
                output[i] = opC[i];
            else
                output[i] = errorMessage[i - 4];
        }
        return output;
    }

    //BCAST
    private void broadCast(String fileName, boolean isAdded){
        
        // create the relevant message
        byte[] bytesName = fileName.getBytes();
        byte[] msg = new byte[bytesName.length + 3];
        msg[0] = (byte)0;
        msg[1] = (byte)9;
        if(isAdded){
            msg[2] = (byte)1;
        }
        else{
            msg[2] = (byte)0;
        }
        for(int i = 0; i < bytesName.length ; i++){
            msg[i+3] = bytesName[i];
        }

        // send message to all logged in clients
        holder.ids_login.forEach((key, logged) -> {
            if (logged == true){
                connections.send(key, msg);
            }
        });
    }

    private static boolean fileExists(String filePath) {
        Path path = Paths.get(filePath);
        return Files.exists(path);
    }

    private static boolean isAccessible(String filePath){
        Path path = Paths.get(filePath);
        return Files.isRegularFile(path);
    }


    private static byte[] extractFile(String path){
        
        try {
            Path file = Paths.get(path);
            byte[] output = Files.readAllBytes(file);
            return output;      
        } catch (IOException e) {e.printStackTrace(); return null;}

    }

    private boolean addNewFile(String fileName, byte[] file){

        Path filePath = Paths.get(directory, fileName);
        try {
            Files.write(filePath, file);
        } catch (IOException e) { return false;}
        return true;
    }

    private byte[] ack(byte first, byte second){
        byte[] output = new byte[4];
        output[0] = (byte)0;
        output[1] = (byte)4;
        output[2] = first;
        output[3] = second;
        return output;
    }

    private byte[] buildFileBytes(Queue<byte[]> queue){

        byte[] file = new byte[UFsize];
        int index = 0;
        while (!queue.isEmpty()){
            byte[] curr = queue.poll();
            for (int i = 6; i < curr.length; i++){
                file[index] = curr[i];
                index++;
            }
        }

        UFsize = 0;
        return file;
    }

    private static byte[] getFileNames(Vector<String> files){

        if (files.isEmpty())
            return new byte[0];

        Vector<byte[]> filebytes = new Vector<>();
        int length = 0;

        for (String file : files){
            byte[] toAdd = file.getBytes();
            filebytes.add(toAdd);
            length += toAdd.length;
        }

        byte[] output = new byte[length + filebytes.size() - 1];
        int index = 0;
        for (byte[] arr : filebytes){
            for (int i = 0; i < arr.length; i++){
                output[index] = arr[i];
                index++;
            }
            if (index < output.length) {
                output[index] = (byte)0;
                index++;
            }
        }

        return output;
    }
}
