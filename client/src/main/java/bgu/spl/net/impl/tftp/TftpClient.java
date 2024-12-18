package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.Socket;
import java.util.Scanner;
import java.io.IOException;
import java.util.Vector;

public class TftpClient {



    public static void main(String[] args) throws IOException{

        if (args.length == 0) {
            args = new String[]{"localhost", "7777"};
        }

        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, message");
            System.exit(1);
        }

        String serverIP = args[0];
        int serverPort =  Integer.parseInt(args[1]); 
        Scanner keyboard = new Scanner(System.in);
        messageQueue<byte[]> pendingMsg = new messageQueue<>();

        TftpEncoderDecoderClient encdec = new TftpEncoderDecoderClient();
        TftpProtocolClient protocol = new TftpProtocolClient();


        // listening thread implementation
        Thread listeningThread = new Thread(() -> {
            
            try (Socket sock = new Socket(serverIP, serverPort)) {

                int read;

                BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
                BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream());

                while(!protocol.shouldTerminate()){ 

                    if (in.available() > 0){
                        read = in.read();

                        if (read >= 0) {
                            byte[] nextMessage = encdec.decodeNextByte((byte) read);
                            if (nextMessage != null) {
                                byte[] msg = protocol.process(nextMessage);
                                if (msg != null)
                                    pendingMsg.put(msg);
                            }
                        }
                    }

                    if (!pendingMsg.isEmpty()) {
                        byte[] msg = pendingMsg.take();
                        out.write(encdec.encode(msg));
                        out.flush();
                    }
                }
            } catch(IOException e) {e.printStackTrace();}
        });

        // keyboard thread implementation
        Thread keyboardThread = new Thread(()-> {

            while (!protocol.shouldTerminate()){
                String input = keyboard.nextLine();
                byte[] toEx = protocol.keyboardProcess(input);
                if (toEx != null)
                    pendingMsg.put(toEx);
                protocol.waitForResponse();
            }
        });

        listeningThread.start();
        keyboardThread.start();

        try {
            keyboardThread.join();
            listeningThread.join();
        } catch (InterruptedException e) {}

        keyboard.close();
   
    }

}

// Concurrent safe queue
class messageQueue<T> {

    private Vector<T> vec;

    public messageQueue(){
        vec = new Vector<>();
    }

    public synchronized void put(T msg){
        vec.add(msg);
    }

    public synchronized T take(){
        return vec.remove(0);
    }

    public synchronized boolean isEmpty(){
        return vec.size() == 0;
    }
}
