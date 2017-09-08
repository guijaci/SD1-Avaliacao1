package edu.utfpr.guilhermej.sisdist.network;

import edu.utfpr.guilhermej.sisdist.listener.MessageEventListener;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MulticastPeer {
    private static final int PORT = 6789;
    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT = 20;

    private final BlockingQueue<String> sendMessageQueue;
    private MulticastSocket s = null;
    private InetAddress group = null;
    private ArrayList<MessageEventListener> messageListeners = new ArrayList<>();

    private boolean executionEnable = false;

    public MulticastPeer(String ip){
        sendMessageQueue = new LinkedBlockingQueue<>();
        executionEnable = true;

        try{
            initMulticastSocket(ip);
            initSendMessageThread();
            initReceiveMessageThread();
        }catch (SocketException e){
            System.out.println("Socket: " + e.getMessage());
            if(s != null) s.close();
        }catch (IOException e){
            System.out.println("IO: " + e.getMessage());
            if(s != null) s.close();
        }
    }

    public void sendMessage(String message){
        if(message == null || message.trim().isEmpty())
            return;
        try {
            sendMessageQueue.put(message);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void addMessageListener(MessageEventListener messageListener){
        messageListeners.add(messageListener);
    }

    public void removeMessageListener(MessageEventListener messageListener){
        messageListeners.remove(messageListener);
    }

    public void disconect() {
        executionEnable = false;
    }

    private void messageReceivedEvent(String message){
        messageListeners.forEach(messageListener -> messageListener.onMessageReceived(message));
    }

    private void initMulticastSocket(String ip) throws IOException{
        group = InetAddress.getByName(ip);
        s = new MulticastSocket(PORT);
        s.setSoTimeout(TIMEOUT);
        s.joinGroup(group);
    }

    private void initReceiveMessageThread() {
        Thread receiveMessageThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            String msg;
            try {
                DatagramPacket messageIn = new DatagramPacket(buffer, buffer.length);
                while (executionEnable) {
                    try {
                        s.receive(messageIn);
                    } catch (SocketTimeoutException e) {
                        Thread.yield();
                        continue;
                    }
                    msg = new String(messageIn.getData()).trim();
                    System.out.println("Received:" + msg);
                    //TODO: Utilizar thread consumidora para nao travar recepção de mensagens
                    messageReceivedEvent(msg);
                    for (int i = 0; i < msg.length(); i++)
                        buffer[i] = 0;
                    messageIn = new DatagramPacket(buffer, buffer.length);
                    Thread.yield();
                }
                s.leaveGroup(group);
            } catch (SocketException e) {
                if (executionEnable) System.out.println("Socket: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("IO: " + e.getMessage());
            } finally {
                if (s != null) s.close();
            }
        });
        receiveMessageThread.setName("Receive Message Thread");
        receiveMessageThread.start();
    }

    private void initSendMessageThread() {
        Thread sendMessageThread = new Thread(() -> {
            String msg;
            byte[] m;
            try {
                while (executionEnable) {
                    if (!sendMessageQueue.isEmpty()) {
                        msg = sendMessageQueue.take();
                        m = msg.getBytes();
                        DatagramPacket messageOut = new DatagramPacket(m, m.length, group, PORT);
                        s.send(messageOut);
                    }
                    Thread.yield();
                }
                s.leaveGroup(group);
            } catch (IOException e) {
                if (executionEnable) System.out.println("IO: " + e.getMessage());
            } catch (InterruptedException e) {
                System.out.println("Interrupted: " + e.getMessage());
            } finally {
                if (s != null) s.close();
            }
        });
        sendMessageThread.setName("Send Message Thread");
        sendMessageThread.start();
    }
}