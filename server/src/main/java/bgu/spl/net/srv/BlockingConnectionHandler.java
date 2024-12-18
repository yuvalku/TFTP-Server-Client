package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.impl.tftp.TftpProtocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

// Added
import java.util.Vector;
import bgu.spl.net.impl.tftp.holder;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {

    private final BidiMessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;

    // Added
    Connections<T> connections;
    private messageQueue<T> pendingMSG;

    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader, BidiMessagingProtocol<T> protocol, Connections<T> connections) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
        this.connections = connections;
        pendingMSG = new messageQueue<>();
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { //just for automatic closing
            int read;

            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());

            // Added
            int id = holder.getMyId();
            connections.connect(id, this);
            protocol.start(id, connections);
            boolean readOK = true;

            while (!protocol.shouldTerminate() && connected && readOK) {

                if (in.available() > 0){
                    read = in.read();
                    readOK = read >= 0;

                    if (read >= 0) {
                        T nextMessage = encdec.decodeNextByte((byte) read);
                        if (nextMessage != null) 
                            protocol.process(nextMessage);
                    }
                }

                if (!pendingMSG.isEmpty()) {
                    T msg = pendingMSG.take();
                    out.write(encdec.encode(msg));
                    out.flush();
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }

    @Override
    public void send(T msg) {
        pendingMSG.put(msg);
    }

    // Added
    public boolean isLoggedIn(){
        return ((TftpProtocol)protocol).isLoggedIn();
    }

    public String getUsername(){
        return ((TftpProtocol)protocol).getUsername();
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