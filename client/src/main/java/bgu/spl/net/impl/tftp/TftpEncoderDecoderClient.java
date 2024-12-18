package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.util.Arrays;

public class TftpEncoderDecoderClient implements MessageEncoderDecoder<byte[]> {

    private byte[] bytes = new byte[1<<10];
    private int len = 0;
    private short opCode;
    private short dataSize;

    @Override
    public byte[] decodeNextByte(byte nextByte) {

        // Extract the opCode
        if (len == 0){
            pushByte(nextByte);
            return null;
        }
        else if (len == 1){
            pushByte(nextByte);
            opCode = (short)(((short)bytes[0]) << 8 | (short)(bytes[1]) & 0x00ff);
            
            if (opCode == 6 | opCode == 10){
                return createOutput();
            }
            return null;
        }

        // RRQ WRQ LOGRQ DELRQ
        else if ((opCode == 1 | opCode == 2 | opCode == 7 | opCode == 8)){
            if (nextByte != 0) {
                pushByte(nextByte);
                return null;
            }
            return createOutput();
        }

        // ACK
        else if (opCode == 4){
            pushByte(nextByte);
            if (len == 4)
                return createOutput();
            return null;
        }

        // BCAST
        else if (opCode == 9){
            if (len == 2) {
                pushByte(nextByte);
                return null;
            }
            else if (nextByte != 0){
                pushByte(nextByte);
                return null;
            }
            return createOutput();
        }

        // DATA
        else if (opCode == 3){
            if (len < 5)
                pushByte(nextByte);
            else if (len == 5){
                pushByte(nextByte);
                dataSize = (short)(((short)bytes[2]) << 8 | (short)(bytes[3]) & 0x00ff);
                if (dataSize == 0)
                    return createOutput();
            }

            else{
                pushByte(nextByte);
                dataSize--;
                if (dataSize == 0) 
                    return createOutput();
            }
            return null;
        }

        // ERROR
        else {
            if (len < 4) {
                pushByte(nextByte);
                return null;
            }
            else if (nextByte != 0){
                pushByte(nextByte);
                return null;
            }
            return createOutput();
        }
    }

    @Override
    public byte[] encode(byte[] message) {

        short _opCode = (short)(((short)message[0]) << 8 | (short)(message[1]));
        if (_opCode == 7 | _opCode == 8 | _opCode == 1 | _opCode == 2 | _opCode == 9 | _opCode == 5) {
            byte[] output = new byte[message.length + 1];
            output[output.length - 1] = 0;
            for (int i = 0; i < output.length - 1; i++)
                output[i] = message[i];
            return output;
        }
        else 
            return message; // maybe need to create a copy
    }

    // Added
    private void pushByte(byte nextByte){
        if (len >= bytes.length)
            bytes = Arrays.copyOf(bytes, len*2);
        bytes[len] = nextByte;
        len++;
    }

    private byte[] createOutput(){
        byte[] output = new byte[len];
        for (int i = 0; i < len; i++){
            output[i] = bytes[i];
        }
        len = 0;
        return output;
    }
}