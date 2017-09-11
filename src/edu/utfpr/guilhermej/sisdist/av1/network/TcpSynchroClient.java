package edu.utfpr.guilhermej.sisdist.av1.network;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

public class TcpSynchroClient implements ISocketConnection {
    private Socket clientSide;

    private final DataOutputStream out;
    private final DataInputStream in;

    public TcpSynchroClient(InetAddress serverAddress, int serverPort) throws IOException {
        clientSide = new Socket(serverAddress, serverPort);

        out = new DataOutputStream(clientSide.getOutputStream());
        in = new DataInputStream(clientSide.getInputStream());
    }

    @Override
    public void setTimeout(int timeout) throws SocketException {
        clientSide.setSoTimeout(timeout);
    }

    @Override
    public void sendMessage(String message) throws IOException {
        if(!isConnected())
            throw new IOException("TCP Connection closed.");
        out.writeUTF(message);
    }

    @Override
    public String getMessage() throws IOException {
        if(!isConnected())
            throw new IOException("TCP Connection closed.");
        String message = in.readUTF();
        System.out.println(String.format("Unicast   [%05d]: %s", getId(), message));
        return message;
    }

    @Override
    public boolean isConnected(){
        return clientSide.isConnected() && !clientSide.isClosed() && clientSide.isConnected() && clientSide.isBound();
    }

    @Override
    public int getId() {
        return clientSide.getLocalPort();
    }

    @Override
    public void disconnect() {
        try {
            if(in != null)
                in.close();
            if(out != null)
                out.close();
            if (clientSide != null && !clientSide.isClosed())
                clientSide.close();
        } catch (IOException e) {
            System.out.println("Client Connection IO: " + e.getMessage());
        }
    }
}
