package edu.utfpr.guilhermej.sisdist.av1.network;

import edu.utfpr.guilhermej.sisdist.av1.listener.INetMessageEventListener;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TcpServerSideClient {
    private TcpServer parent;
    private Socket clientSocket;
    private BlockingQueue<String> sendMessageQueue;

    private List<INetMessageEventListener> messageListeners;

    private boolean executionEnable;

    public TcpServerSideClient(TcpServer parent, Socket clientSocket){
        this.parent = parent;
        this.clientSocket = clientSocket;

        sendMessageQueue = new LinkedBlockingQueue<>();

        initConnectionReceiveMessageThread();
        initConnectionSendMessageThread();
    }

    public boolean isConnected() {
        return executionEnable && clientSocket.isConnected();
    }

    public void disconnect() {
        executionEnable = false;
        try {
            clientSocket.close();
        } catch (IOException e) {
            System.out.printf("TCP Connection Disconect: " + e.getMessage());
        }
    }

    public TcpServer getParent() {
        return parent;
    }

    public void sendMessage(String message){
        sendMessageQueue.add(message);
    }

    public void addMessageReceivedListener(INetMessageEventListener listener){
        messageListeners.add(listener);
    }

    public void removeMessageReceivedListener(INetMessageEventListener listener){
        messageListeners.remove(listener);
    }

    private void initConnectionReceiveMessageThread() {
        Thread connection = new Thread(() -> {
            try {
                DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                while (executionEnable) {
                    if(clientSocket.isConnected() && !clientSocket.isClosed() ) {
                        String msg = in.readUTF();
                        if(msg == null) {
                            Thread.yield();
                            continue;
                        }
                        messageReceivedEvent(in.readUTF(),
                                InetAddress.getByName(clientSocket.getRemoteSocketAddress().toString()));
                        Thread.yield();
                    }
                }
                in.close();
                if(clientSocket != null && !clientSocket.isClosed())
                    clientSocket.close();
            }catch(IOException e){
                System.out.println("TCP Server Side Receive: " + e.getMessage());
            }
        });
        connection.setName("TCP Server Side Receive Message Thread: "+clientSocket.toString());
        connection.start();
    }

    private void initConnectionSendMessageThread(){
        Thread connection =  new Thread(()->{
            try{
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
                while(executionEnable){
                    if(clientSocket.isConnected() && !clientSocket.isClosed()){
                        if(!sendMessageQueue.isEmpty()) {
                            out.writeUTF(sendMessageQueue.take());
                        }
                        Thread.yield();
                    }
                }
                out.close();
                if(clientSocket != null && !clientSocket.isClosed())
                    clientSocket.close();
            }catch (IOException e){
                System.out.println("TCP Server Side IO: " + e.getMessage());
            } catch (InterruptedException e) {
                System.out.println("TCP Server Side Interrupted: " + e.getMessage());
            }
        });
        connection.setName("TCP Server Side Send Message Thread: "+clientSocket.toString());
        connection.start();
    }

    private void messageReceivedEvent(String message, InetAddress address){
        messageListeners.forEach(listener -> listener.onNetMessageReceived(message, address));
    }
}
