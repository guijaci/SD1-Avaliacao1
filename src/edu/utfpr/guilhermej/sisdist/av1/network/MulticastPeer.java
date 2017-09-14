package edu.utfpr.guilhermej.sisdist.av1.network;

import edu.utfpr.guilhermej.sisdist.av1.listener.INetMessageEventListener;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Envelope para conexões multicast
 */
public class MulticastPeer {
    /** porta do socket multicast */
    private static final int PORT = 6789;
    /** tamanho do buffer para recepção de mensagens */
    private static final int BUFFER_SIZE = 1024;
    /** timeout de recepção de mensagens */
    private static final int TIMEOUT = 20;

    /** fila para comunicar threads internas de mensagens para envio */
    private final BlockingQueue<String> sendMessageQueue;
    /** fila para comunicar threads internas de mensagens capturadas */
    private final BlockingQueue<NetAddressedMessage> receiveMessageQueue;
    /** soquete multicast em si */
    private MulticastSocket multicastSocket = null;
    /** endereço do grupo multicast */
    private InetAddress group = null;
    /** observadores de mensagens */
    private List<INetMessageEventListener> messageListeners = new ArrayList<>();

    private boolean executionEnable = false;

    /**
     * Construtor padrão
     * @param ip endereço IP do grupo multicast
     */
    public MulticastPeer(String ip){
        //Inicialização de filas de comunicação
        sendMessageQueue = new LinkedBlockingQueue<>();
        receiveMessageQueue = new LinkedBlockingQueue<>();
        executionEnable = true;

        //inicializações de conexão e threads
        try{
            initMulticastSocket(ip);
            initSendMessageThread();
            initReceiveMessageThread();
            initPropagateMessageThread();
        }catch (SocketException e){
            e.printStackTrace();
            if(multicastSocket != null) multicastSocket.close();
        }catch (IOException e){
            e.printStackTrace();
            if(multicastSocket != null) multicastSocket.close();
        }
    }

    /**
     * Coloca uma mensagem em fila para ser enviada
     * @param message mensagem para enviar
     */
    public void sendMessage(String message){
        if(message == null || message.trim().isEmpty())
            return;
        try {
            sendMessageQueue.put(message);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Inscreve observadores para receber mensagens dessa conexão
     * @param messageListener observador inscrito
     */
    public void addMessageListener(INetMessageEventListener messageListener){
        messageListeners.add(messageListener);
    }

    /**
     * Cancela inscrição de observadores para receber mensagens dessa conexão
     * @param messageListener observador desinscrito
     */
    public void removeMessageListener(INetMessageEventListener messageListener){
        messageListeners.remove(messageListener);
    }

    /**
     * Desconecta conexão multicast e finaliza threads de escrita/ leitura
     */
    public void disconect() {
        executionEnable = false;
    }

    /**
     * Inicia soquete multicasst
     * @param ip ip do grupo multicast
     * @throws IOException caso não seja possível criar soquete
     */
    private void initMulticastSocket(String ip) throws IOException{
        group = InetAddress.getByName(ip);
        multicastSocket = new MulticastSocket(PORT);
        multicastSocket.setSoTimeout(TIMEOUT);
        multicastSocket.joinGroup(group);
    }

    /**
     * Inicializa thread de recepção de mensagens
     */
    private void initReceiveMessageThread() {
        Thread receiveMessageThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            String message;
            try {
                DatagramPacket messageIn = new DatagramPacket(buffer, buffer.length);
                while (executionEnable) {
                    try {
                        //Permanece esperando mensagem até receber ou estourar timeout...
                        multicastSocket.receive(messageIn);
                    } catch (SocketTimeoutException e) {
                        //... se timeout estourar, reinicie loop
                        Thread.yield();
                        continue;
                    }
                    message = new String(messageIn.getData()).trim();
                    System.out.println(String.format("Multicast [%05d]: %s", getId(), message));
                    //Coloca mensagem recebida em fila para ser propagada
                    receiveMessageQueue.add(new NetAddressedMessage()
                            .setMessage(message)
                            .setSenderAddress(messageIn.getAddress()));
                    //limpa buffer
                    for (int i = 0; i < message.length(); i++)
                        buffer[i] = 0;
//                    messageIn = new DatagramPacket(buffer, buffer.length);
                    Thread.yield();
                }
                //AO desconectar, saia do grupo
                multicastSocket.leaveGroup(group);
            } catch (SocketException e) {
                if (executionEnable) e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (multicastSocket != null) multicastSocket.close();
            }
        });
        receiveMessageThread.setName("Receive Message Thread");
        receiveMessageThread.start();
    }

    /**
     * Inicializa thread para envi de mensagens
     */
    private void initSendMessageThread() {
        Thread sendMessageThread = new Thread(() -> {
            String msg;
            byte[] m;
            try {
                while (executionEnable) {
                    //Enquanto houver mensagens para enviar,
                    //retire-as da pilha e envie
                    if (!sendMessageQueue.isEmpty()) {
                        msg = sendMessageQueue.take();
                        m = msg.getBytes();
                        DatagramPacket messageOut = new DatagramPacket(m, m.length, group, PORT);
                        multicastSocket.send(messageOut);
                    }
                    Thread.yield();
                }
                //Ao final da execução, tente sair do grupo
                multicastSocket.leaveGroup(group);
            } catch (IOException e) {
                if (executionEnable) e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                if (multicastSocket != null) multicastSocket.close();
            }
        });
        sendMessageThread.setName("Send Message Thread");
        sendMessageThread.start();
    }

    /**
     * Inicializa thread para propagar mensagens para observadores inscritos
     */
    private void initPropagateMessageThread(){
        Thread propagateMessage = new Thread(()->{
            while(executionEnable){
                synchronized (receiveMessageQueue) {
                    if (receiveMessageQueue.isEmpty()) {
                        Thread.yield();
                        continue;
                    }
                    try {
                        //Enquanto houver mensagens para serem propagadas, retire-as da fila e envie a cada observador
                        NetAddressedMessage addressedMessage = receiveMessageQueue.take();
                        netMessageReceivedEvent(addressedMessage.getMessage(), addressedMessage.getSenderAddress());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Thread.yield();
            }
        });
        propagateMessage.setName("Propagate Message Thread");
        propagateMessage.start();
    }

    /**
     * Lança um evento de mensagem de rede recebida
     * @param message mensagem recebida
     * @param address endereço do remetente da mensagem
     */
    private void netMessageReceivedEvent(String message, InetAddress address){
        messageListeners.forEach(listener->listener.onNetMessageReceived(message, address));
    }

    /**
     * Retorna identificador da porta desta conexão
     * @return valor de porta conectada neste soquete
     */
    public int getId() {
        return multicastSocket.getLocalPort();
    }

    /**
     * Classe de mensagens associadas com endereço de ip de remetentes
     */
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