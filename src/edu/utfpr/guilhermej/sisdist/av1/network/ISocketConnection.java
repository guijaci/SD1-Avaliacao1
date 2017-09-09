package edu.utfpr.guilhermej.sisdist.av1.network;

import java.io.IOException;
import java.net.SocketException;

public interface ISocketConnection {
    void setTimeout(int timeout) throws SocketException;

    void sendMessage(String message) throws IOException;

    String getMessage() throws IOException;

    boolean isConnected();

    void disconnect();
}
