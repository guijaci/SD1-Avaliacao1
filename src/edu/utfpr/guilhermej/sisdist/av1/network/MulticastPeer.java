package edu.utfpr.guilhermej.sisdist.av1.network;

import edu.utfpr.guilhermej.sisdist.av1.listener.INetMessageEventListener;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MulticastPeer {
    private static final int PORT = 6789;
    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT = 20;

    private final BlockingQueue<String> sendMessageQueue;
    private final BlockingQueue<NetAddressedMessage> receiveMessageQueue;
    private MulticastSocket multicastSocket = null;
    private InetAddress group = null;
    private List<INetMessageEventListener> messageListeners = new ArrayList<>();

    private boolean executionEnable = false;

    public MulticastPeer(String ip){
        sendMessageQueue = new LinkedBlockingQueue<>();
        receiveMessageQueue = new LinkedBlockingQueue<>();
        executionEnable = true;

        try{
            initMulticastSocket(ip);
            initSendMessageThread();
            initReceiveMessageThread();
            initPropagateMessageThread();
        }catch (SocketException e){
            System.out.println("Socket: " + e.getMessage());
            if(multicastSocket != null) multicastSocket.close();
        }catch (IOException e){
            System.out.println("IO: " + e.getMessage());
            if(multicastSocket != null) multicastSocket.close();
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

    public void addMessageListener(INetMessageEventListener messageListener){
        messageListeners.add(messageListener);
    }

    public void removeMessageListener(INetMessageEventListener messageListener){
        messageListeners.remove(messageListener);
    }

    public void disconect() {
        executionEnable = false;
    }

    private void messageReceivedEvent(String message, InetAddress addresss){
        messageListeners.forEach(messageListener -> messageListener.onNetMessageReceived(message, addresss));
    }

    private void initMulticastSocket(String ip) throws IOException{
        group = InetAddress.getByName(ip);
        multicastSocket = new MulticastSocket(PORT);
        multicastSocket.setSoTimeout(TIMEOUT);
        multicastSocket.joinGroup(group);
    }

    private void initReceiveMessageThread() {
        Thread receiveMessageThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            String message;
            try {
                DatagramPacket messageIn = new DatagramPacket(buffer, buffer.length);
                while (executionEnable) {
                    try {
                        multicastSocket.receive(messageIn);
                    } catch (SocketTimeoutException e) {
                        Thread.yield();
                        continue;
                    }
                    message = new String(messageIn.getData()).trim();
                    System.out.println(String.format("Multicast [%05d]: %s", getId(), message));
                    receiveMessageQueue.add(new NetAddressedMessage()
                            .setMessage(message)
                            .setSenderAddress(messageIn.getAddress()));
                    for (int i = 0; i < message.length(); i++)
                        buffer[i] = 0;
                    messageIn = new DatagramPacket(buffer, buffer.length);
                    Thread.yield();
                }
                multicastSocket.leaveGroup(group);
            } catch (SocketException e) {
                if (executionEnable) System.out.println("Socket: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("IO: " + e.getMessage());
            } finally {
                if (multicastSocket != null) multicastSocket.close();
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
                        multicastSocket.send(messageOut);
                    }
                    Thread.yield();
                }
                multicastSocket.leaveGroup(group);
            } catch (IOException e) {
                if (executionEnable) System.out.println("IO: " + e.getMessage());
            } catch (InterruptedException e) {
                System.out.println("Interrupted: " + e.getMessage());
            } finally {
                if (multicastSocket != null) multicastSocket.close();
            }
        });
        sendMessageThread.setName("Send Message Thread");
        sendMessageThread.start();
    }

    private void initPropagateMessageThread(){
        Thread propagateMessage = new Thread(()->{
            while(executionEnable){
                synchronized (receiveMessageQueue) {
                    if (receiveMessageQueue.isEmpty()) {
                        Thread.yield();
                        continue;
                    }
                    try {
                        NetAddressedMessage addressedMessage = receiveMessageQueue.take();
                        netMessageReceivedEvent(addressedMessage.getMessage(), addressedMessage.getSenderAddress());
                    } catch (InterruptedException e) {
                        System.out.println("Interrupted: " + e.getMessage());
                    }
                }
                Thread.yield();
            }
        });
        propagateMessage.setName("Propagate Message Thread");
        propagateMessage.start();
    }

    private void netMessageReceivedEvent(String message, InetAddress address){
        messageListeners.forEach(listener->listener.onNetMessageReceived(message, address));
    }

    public int getId() {
        return multicastSocket.getLocalPort();
    }

    private class NetAddressedMessage{
        private String message;
        private InetAddress senderAddress;

        public String getMessage() {
            return message;
        }

        public NetAddressedMessage setMessage(String message) {
            this.message = message;
            return this;
        }

        public InetAddress getSenderAddress() {
            return senderAddress;
        }

        public NetAddressedMessage setSenderAddress(InetAddress senderAddress) {
            this.senderAddress = senderAddress;
            return this;
        }
    }
}