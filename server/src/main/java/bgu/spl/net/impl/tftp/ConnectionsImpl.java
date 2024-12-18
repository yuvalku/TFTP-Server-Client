package bgu.spl.net.impl.tftp;

import java.util.concurrent.ConcurrentHashMap;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionHandler;

public class ConnectionsImpl<T> implements Connections<T> {

    private ConcurrentHashMap<Integer, ConnectionHandler<T>> clientsHandlers;

    // constructor
    public ConnectionsImpl(){
        clientsHandlers = new ConcurrentHashMap<Integer, ConnectionHandler<T>>();
    }

    // add a new handler and id to the hash map, called when socket connected
    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler){
        clientsHandlers.put(connectionId, handler);
    }

    // check if the client is logged in and send the message if it does, return if message sent successfully
    @Override
    public boolean send(int connectionId, T msg){
        if (!clientsHandlers.containsKey(connectionId))
            return false;
        ConnectionHandler<T> handler = clientsHandlers.get(connectionId);
        handler.send(msg);
        return true;
    }

    // remove the client from the hash map
    @Override
    public void disconnect(int connectionId){
        clientsHandlers.remove(connectionId);
    }
    
}
