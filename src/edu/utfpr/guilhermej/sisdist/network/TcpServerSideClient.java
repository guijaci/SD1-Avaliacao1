package edu.utfpr.guilhermej.sisdist.network;

import edu.utfpr.guilhermej.sisdist.listener.NetMessageEventListener;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TcpServerSideClient {
    private TcpServer parent;
    private Socket clientSocket;
    private BlockingQueue<String> sendMessageQueue;

    private List<NetMessageEventListener> messageListeners;

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

    public void addMessageReceivedListener(NetMessageEventListener listener){
        messageListeners.add(listener);
    }

    public void removeMessageReceivedListener(NetMessageEventListener listener){
        messageListeners.remove(listener);
    }

    private void initConnectionReceiveMessageThread() {
        Thread connection = new Thread(() -> {
            try {
                Scanner in = new Scanner(clientSocket.getInputStream());
                while (executionEnable) {
                    if(clientSocket.isConnected() && !clientSocket.isClosed() ) {
                        if(in.hasNextLine())
                            messageReceivedEvent(in.nextLine(),
                                    InetAddress.getByName(clientSocket.getRemoteSocketAddress().toString()));
                        Thread.yield();
                    }
                }
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
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream());
                while(executionEnable){
                    if(clientSocket.isConnected() && !clientSocket.isClosed()){
                        if(!sendMessageQueue.isEmpty())
                            out.println(sendMessageQueue.take());
                        Thread.yield();
                    }
                }
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
