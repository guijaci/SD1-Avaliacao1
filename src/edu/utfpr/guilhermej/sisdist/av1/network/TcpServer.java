package edu.utfpr.guilhermej.sisdist.av1.network;

import edu.utfpr.guilhermej.sisdist.av1.listener.ITcpSynchroConnectionEventListener;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class TcpServer {
    private final int TIMEOUT = 3000;

    ServerSocket listenSocket;

    private ArrayList<ITcpSynchroConnectionEventListener> connectionListeners;
    private ArrayList<TcpServerSideClient> clientConnections;
    private int port;
    private boolean executionEnable = false;

    public TcpServer(int port) throws IOException {
        this.port = port;
        listenSocket = new ServerSocket(port);
        clientConnections = new ArrayList<>();
        connectionListeners = new ArrayList<>();
        executionEnable = true;
//        listenSocket.setSoTimeout(TIMEOUT);
        initReconectionThread();
    }

    public void disconnect(){
        executionEnable = false;
        clientConnections.forEach(TcpServerSideClient::disconnect);
        try{
            if(listenSocket != null && !listenSocket.isClosed())
                listenSocket.close();
        } catch (IOException e) {
                System.out.println("Server IO: " + e.getMessage());
        }
    }

    public void addTcpConnectionListener(ITcpSynchroConnectionEventListener connectionListener){
        connectionListeners.add(connectionListener);
    }

    public void removeTcpConnectionListener(ITcpSynchroConnectionEventListener connectionListener){
        connectionListeners.remove(connectionListener);
    }

    public InetAddress getIpAddress() throws UnknownHostException {
        return InetAddress.getLocalHost();
    }

    private void initReconectionThread() {
        Thread reconnectThread = new Thread(()->{
            try {
                Socket clientSocket;
                while (executionEnable) {
                    try {
                        clientSocket = listenSocket.accept();
                    } catch (IOException e) {
                        Thread.yield();
                        continue;
                    }
                    TcpSynchroServerSideClient connection = new TcpSynchroServerSideClient(this, clientSocket);
                    tcpClientConnectionEvent(connection);
                }
                if(listenSocket != null && !listenSocket.isClosed())
                    listenSocket.close();
            } catch (IOException e) {
                System.out.println("TCP Server IO: "+ e.getMessage());
            }
        });
        reconnectThread.setName("TCP Server");
        reconnectThread.start();
    }

    private void tcpClientConnectionEvent(TcpSynchroServerSideClient connection){
        connectionListeners.forEach(listener -> listener.onTcpSecureConectionEventListener(connection));
    }
}
