package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;
import java.nio.charset.StandardCharsets;

import bgu.spl.net.api.MessagingProtocol;

public class TftpProtocolClient implements MessagingProtocol<byte[]>  {

    private boolean shouldTerminate;
    private Object termiLock = new Object();
    private short flag = -1; 
    private Object flagLock = new Object();
    private Queue<byte[]> uploadingFile = new LinkedList<>();
    private int UFsize = 0;
    private String uploadingFileName;
    private final String directory = ".";
    private sendingFile toSend = null;
    private Path pathToNewFile;
    private String wrqfileName;
    private Object waitingLock = new Object();
    private boolean toWait = true;

    
    public byte[] process(byte[] msg) {

        short opCode = (short)(((short)msg[0]) << 8 | (short)(msg[1]) & 0x00ff);
        byte[] output = null;
        
        // DATA
        if (opCode == 3){
            // add data packet to queue
            uploadingFile.add(msg);

            // send acknowledgment
            output = ack(msg[4], msg[5]);

            // check data's size
            short packetSize = (short)(((short)msg[2]) << 8 | (short)(msg[3]) & 0x00ff);
            UFsize += packetSize;

            if (packetSize < 512) {
                 
                // RRQ
                if (readFlag() == 1){
                    byte[] file = buildFileBytes(uploadingFile);
                    addNewFile(file);
                    System.out.println("RRQ " + uploadingFileName + " complete");
                    setFlag((short)-1);
                }
                
                //DIRQ
                else {
                    Vector<String> files = buildFileNames(uploadingFile);
                    System.out.println();
                    System.out.println("Directory Files:");
                    for (String s : files){
                        System.out.println(s);
                    }
                    System.out.println();
                    setFlag((short)-1);
                }

                wakeKeyboard();
            }
        }

        // ACK
        else if(opCode == 4){

            short blockNum = (short)(((short)msg[2]) << 8 | (short)(msg[3]) & 0x00ff);
            System.out.println("ACK " + blockNum);

            // handle DATA packet if needed
            if (readFlag() == 2) {
                if (toSend.isFinished()){
                    setFlag((short)-1);
                    System.out.println("WRQ " + wrqfileName + " complete");
                    wakeKeyboard();
                }
                else
                    output = toSend.generatePacket();
            }

            // handle disconnect
            else if (readFlag() == 10)
                terminate();

            // check if need to reset flag
            if (blockNum == 0 & readFlag() != 2) {
                setFlag((short)-1);
                wakeKeyboard();
            }

        }

        // BCAST
        else if (opCode == 9){

            String added;
            if (msg[2] == (byte)1)
                added = "add";
            else
                added = "del";
            
            String toPrint = new String(msg, 3, msg.length - 3, StandardCharsets.UTF_8);
            System.out.println("BCAST " + added + " " + toPrint);
        }

        // Error
        else {

            // print error
            short errorNum = (short)(((short)msg[2]) << 8 | (short)(msg[3]) & 0x00ff);
            String errorMsg = new String(msg, 4, msg.length - 4, StandardCharsets.UTF_8);
            System.out.println("Error " + errorNum + " " + errorMsg);

            // RRQ ERROR
            if (readFlag() == 1){

                // delete file from directory
                String path = directory + "/" + uploadingFileName;
                File tempFile = new File(path);
                tempFile.delete();

                // reset uploading file data
                while (!uploadingFile.isEmpty())
                    uploadingFile.poll();
                UFsize = 0;
            }

            // DISC ERROR
            if (readFlag() == 10)
                terminate();

            // Reset flag
            setFlag((short)-1);
            wakeKeyboard();
        }

        return output;
    }

    ////////////////////keyboardProcess////////////////////
    public byte[] keyboardProcess(String str){

        byte[] output = null;
        String[] input = str.split("\\s+");
        String cmd = input[0];

        if (cmd.equals("LOGRQ")){

            setFlag((short)7);

            // extract name and create logrq packet
            String name = input[1];
            byte[] bytesName = name.getBytes();
            output = new byte[bytesName.length + 2];
            output[0] = (byte)0;
            output[1] = (byte)7;
            for (int i = 0; i < bytesName.length; i++)
                output[i + 2] = bytesName[i];
        }

        else if (cmd.equals("DELRQ")){

            setFlag((short)8);

            // extract name and create delrq packet
            String file = "";

            for(int i = 1 ; i < input.length ; i ++){
                file = file + " " + input[i];
            }
            file = file.substring(1);
            byte[] fileName = file.getBytes();
            output = new byte[fileName.length + 2];
            output[0] = (byte)0;
            output[1] = (byte)8;
            for (int i = 0; i < fileName.length; i++)
                output[i + 2] = fileName[i];
        }

        else if (cmd.equals("RRQ")){

            // extract name and create rrq packet if doesn't exists here
            String file = "";
            
            for(int i = 1 ; i < input.length ; i ++){
                file = file + " " + input[i];
            }
            file = file.substring(1);
            uploadingFileName = file;
            if (fileExists(directory + "/" + uploadingFileName)){
                System.out.println("file already exists");
                toWait = false;
            }

            else {

                // set flag
                setFlag((short)1);
                
                // create path
                pathToNewFile = Paths.get(directory, uploadingFileName);

                byte[] fileName = uploadingFileName.getBytes();
                output = new byte[fileName.length + 2];
                output[0] = (byte)0;
                output[1] = (byte)1;
                for (int i = 0; i < fileName.length; i++)
                    output[i + 2] = fileName[i];
            }
        }

        else if (cmd.equals("WRQ")){

            String file = "";
            
            for(int i = 1 ; i < input.length ; i ++){
                file = file + " " + input[i];
            }
            file = file.substring(1);
            wrqfileName = file;

            String path = directory + "/"+ wrqfileName;
            if (!fileExists(path)){
                System.out.println("file does not exists");
                toWait = false;
            }

            else {

                // set flag
                setFlag((short)2);

                // set toSend
                toSend = new sendingFile(extractFile(directory + "/" + wrqfileName));

                byte[] fileName = wrqfileName.getBytes();
                output = new byte[fileName.length + 2];
                output[0] = (byte)0;
                output[1] = (byte)2;
                for (int i = 0; i < fileName.length; i++)
                    output[i + 2] = fileName[i];
            }
        }

        else if (cmd.equals("DIRQ")){
            setFlag((short)6);
            output = new byte[2];
            output[0] = (byte)0;
            output[1] = (byte)6;
        }

        else if (cmd.equals("DISC")){
            setFlag((short)10);
            output = new byte[2];
            output[0] = (byte)0;
            output[1] = (byte)10;
        }

        else{
            System.out.println("Illegal input, try again");
            toWait = false;
        }

        return output;
    }


    public boolean shouldTerminate() {
        boolean output;
        synchronized (termiLock) {output = shouldTerminate;}
        return output;
    }

    private void terminate(){
        synchronized (termiLock) {shouldTerminate = true;}
    }

    private byte[] ack(byte first, byte second){
        byte[] output = new byte[4];
        output[0] = (byte)0;
        output[1] = (byte)4;
        output[2] = first;
        output[3] = second;
        return output;
    }

    //listening Thread
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
    //listening Thread
    private boolean addNewFile(byte[] file){

        try {
            Files.write(pathToNewFile, file);
        } catch (IOException e) { return false;}
        return true;
    }

    //listening Thread
    private Vector<String> buildFileNames(Queue<byte[]> queue){

        Vector<String> output = new Vector<>();
        
        byte[] allbytes = new byte[UFsize];
        int index = 0;
        while (!queue.isEmpty()){
            byte[] curr = queue.poll();
            for (int i = 6; i < curr.length; i++){
                allbytes[index] = curr[i];
                index++;
            }
        }
        UFsize = 0;

        index = 0;
        int start = 0;

        while (index < allbytes.length) {

            if (allbytes[index] == (byte)0){
                output.add(new String(allbytes, start, index - start, StandardCharsets.UTF_8));
                start = index + 1;
            }
            index++;
        }

        output.add(new String(allbytes, start, allbytes.length - start, StandardCharsets.UTF_8));

        return output;
    }
    
    // keyboard thread
    private static boolean fileExists(String filePath) {
        Path path = Paths.get(filePath);
        return Files.exists(path);
    }

    // keyboard thread
    private static byte[] extractFile(String path){
        
        try {
            Path file = Paths.get(path);
            byte[] output = Files.readAllBytes(file);
            return output;      
        } catch (IOException e) {e.printStackTrace(); return null;}

    }

    private int readFlag(){
        int output;
        synchronized (flagLock) {output = flag;}
        return output;
    }

    private void setFlag(short num){
        synchronized (flagLock) {flag = num;}
    }

    public void waitForResponse(){
        synchronized (waitingLock) {
            try {
                if (toWait) {
                    waitingLock.wait();
                }
            }    catch (InterruptedException e) {}
        }
        toWait = true;
    }

    private void wakeKeyboard(){
        synchronized (waitingLock) {
            waitingLock.notifyAll();
        }
    }
}

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