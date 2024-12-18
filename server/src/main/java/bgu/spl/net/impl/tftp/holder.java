package bgu.spl.net.impl.tftp;

// Added
import java.util.concurrent.ConcurrentHashMap;
import java.util.Vector;

public class holder{

    public static ConcurrentHashMap<Integer, Boolean> ids_login = new ConcurrentHashMap<>();
    public static Vector<String> usernames = new Vector<>();
    private static Integer idCounter = 0;
    private static Object idLock = new Object();

    public static int getMyId(){
        int output;
        synchronized (idLock) {
            output = idCounter;
            idCounter++;
        }
        return output;
    }
    
}
