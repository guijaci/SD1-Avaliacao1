package edu.utfpr.guilhermej.sisdist.av1.network;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class TcpSynchroServerSideClient implements ISocketConnection {
    private TcpServer parent;
    private Socket clientSocket;
    private DataOutputStream out;
    private DataInputStream in;

    private boolean executionEnable = false;

    public TcpSynchroServerSideClient(TcpServer parent, Socket clientSocket){
        this.parent = parent;
        this.clientSocket = clientSocket;

        executionEnable = true;

        try {
            out = new DataOutputStream(clientSocket.getOutputStream());
            in = new DataInputStream(clientSocket.getInputStream());
        } catch (IOException e) {
            System.out.println("TCP Conection IO: "+e.getMessage());
        }
    }

    public TcpServer getParent() {
    return parent;
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
    public void sendMessage(String message) throws IOException{
        if(!isConnected())
            throw new IOException("TCP Connection closed.");
        out.writeUTF(message);
    }

    @Override
    public void setTimeout(int timeout) throws SocketException {
        clientSocket.setSoTimeout(timeout);
    }

    @Override
    public boolean isConnected() {
        return executionEnable && clientSocket.isConnected() && !clientSocket.isClosed() && clientSocket.isBound();
    }

    @Override
    public int getId() {
        return clientSocket.getPort();
    }

    @Override
    public void disconnect() {
        executionEnable = false;
        try {
            if(in != null)
                in.close();
            if(out != null)
                out.close();
            if (clientSocket != null && !clientSocket.isClosed())
                clientSocket.close();
        } catch (IOException e) {
            System.out.println("Server Side Client Connection IO: " + e.getMessage());
        }
    }
}
