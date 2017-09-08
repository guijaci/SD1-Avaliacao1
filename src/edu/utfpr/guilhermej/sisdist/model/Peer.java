package edu.utfpr.guilhermej.sisdist.model;

import edu.utfpr.guilhermej.sisdist.listener.MessageEventListener;
import edu.utfpr.guilhermej.sisdist.listener.NetMessageEventListener;
import edu.utfpr.guilhermej.sisdist.network.MulticastPeer;
import edu.utfpr.guilhermej.sisdist.network.TcpServer;
import edu.utfpr.guilhermej.sisdist.network.TcpSynchroServerSideClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Peer {
    public static final int TCP_TIMEOUT = 500;
    //Constantes para criptografia
    private final int KEY_LENGTH = 512;
    private final String ENCRYPTION_ALGORITHM = "RSA";
    //IP multicast
    private final String MULTICAST_IP_ADD = "233.32.31.30";
    //Intervalo de tempo em que um indexador envia mensagens
    private final int DELTA = 2500;

    private UUID uuid;
    private KeyPair keyPair;
    private MulticastPeer multicastPeer;
    private TcpServer tcpServer;

    private PeerOpponent lastActiveIndexer;
    private Map<UUID, PeerOpponent> peerMap = new HashMap<>();

    private List<MessageEventListener> messageListeners;

    private int tcpPort;
    private boolean executionEnable = false;
    private boolean indexerUp = false;
    private boolean indexing = false;

    public Peer(){
        messageListeners = new ArrayList<>();

        uuid = UUID.randomUUID();
        keyPair = buildKeyPair(KEY_LENGTH, ENCRYPTION_ALGORITHM);
        multicastPeer = new MulticastPeer(MULTICAST_IP_ADD);
        multicastPeer.addMessageListener(this::processMulticastMessage);

        tcpPort = 1024 + new Random().nextInt(65535-1024);
        try{
            tcpServer = new TcpServer(tcpPort);
        }catch (IOException e){
            System.out.println("TCP Server: " + e.getMessage());
        }
        tcpServer.addTcpConnectionListener(this::onTcpClientConnect);

        peerMap.put(uuid,
                new PeerOpponent()
                .setPortTcp(tcpPort)
                .setUuid(uuid));

        executionEnable = true;
        initPeerThread();
    }

    public void disconnect() {
        multicastLeavingMessage();
        executionEnable = false;
        tcpServer.disconnect();
        multicastPeer.disconect();
    }

    public void sendMulticastMessage(String message) {
        multicastPeer.sendMessage(message);
    }

    public void processMulticastMessage(String message, InetAddress address){
        String msgTokens[] = message.split("/");
        UUID senderUuid = UUID.fromString(msgTokens[1]);
        if(uuid.equals(senderUuid)) return;
        switch (msgTokens[0]){
            case "GREETING":
                multicastInviteMessage(senderUuid);
                if(!peerMap.containsKey(senderUuid)){
                    peerMapPut(senderUuid,
                            new PeerOpponent()
                            .setUuid(senderUuid)
                            .setIpAddress(address)
                            .setPortTcp(Integer.parseInt(msgTokens[2])));
                }
                break;
            case "INDEXING":
                setIndexerUp(true);
                setIndexing(false);
                if(!peerMap.containsKey(senderUuid)) {
                    peerMapPut(senderUuid,
                            new PeerOpponent()
                            .setUuid(senderUuid)
                            .setIpAddress(address)
                            .setPortTcp(Integer.parseInt(msgTokens[2])));
                }
                if(lastActiveIndexer != null) {
                    if(!senderUuid.equals(lastActiveIndexer.getUuid())) {
                        lastActiveIndexer = peerMap.get(senderUuid);
                        sendPublicKey(lastActiveIndexer);
                    }
                }
                else
                    lastActiveIndexer = peerMap.get(senderUuid);
                break;
            case "INVITE":
                UUID invited = UUID.fromString(msgTokens[2]);
                if(uuid.equals(invited) && !peerMap.containsKey(senderUuid)) {
                    peerMapPut(senderUuid,
                            new PeerOpponent()
                            .setIpAddress(address)
                            .setPortTcp(Integer.parseInt(msgTokens[3]))
                            .setUuid(senderUuid));
                }
                break;
            case "LEAVING":
                if(peerMap.containsKey(senderUuid))
                    peerMapRemove(senderUuid);
                break;
            default:
        }
        messageListeners.forEach(listener->listener.onMessageReceived(message));
    }

    public void addMulticastMessageListener(MessageEventListener messageListener){
        messageListeners.add(messageListener);
    }

    public void removeMulticatsMesageListener(NetMessageEventListener messageListener){
        messageListeners.remove(messageListener);
    }

    @Override
    public String toString() {
        return String.format("Peer ID: [%s]- TCP Port: [%d]",uuid.toString(),tcpPort);
    }

    private KeyPair buildKeyPair(int length, String algorithm){
        KeyPairGenerator keyGen;
        try{
            keyGen = KeyPairGenerator.getInstance(algorithm);
        }catch (NoSuchAlgorithmException e){
            throw new RuntimeException("Expected "+ENCRYPTION_ALGORITHM+" algorithm for encryption", e);
        }
        keyGen.initialize(length);
        return keyGen.generateKeyPair();
    }

    private void initPeerThread(){
        Thread peerThread = new Thread(() -> {
            multicastGreetingMessage();
            delay();
            while(executionEnable){
                if(peerMap.size() > 3) {
                    if (indexing)
                        multicastIndexingMessage();
                    else {
                        if(indexerUp){
                            setIndexerUp(false);
                            delay();
                        }
                        else{
                            List<PeerOpponent> peers = new ArrayList<>(peerMap.values());
                            peers.sort(Comparator.comparing(PeerOpponent::getUuid));
                            if(uuid.equals(peers.get(0).getUuid())){
                                setIndexing(true);
                                lastActiveIndexer = peers.get(0);
                                continue;
                            }
                            delay(2);
                            if(!indexerUp){
                                peerMapRemove(peers.get(0).getUuid());
                                continue;
                            }
                        }
                    }
                }
                else
                    setIndexing(false);
                delay();
            }
        });
        peerThread.setName("Peer Sinchronyzation Thread");
        peerThread.start();
    }

    private void multicastIndexingMessage() {
        sendMulticastMessage(String.format("INDEXING/%s/%d",
                uuid.toString(), tcpPort));
    }

    private void multicastInviteMessage(UUID invited) {
        sendMulticastMessage(String.format("INVITE/%s/%s/%d",
                uuid.toString(), invited.toString(), tcpPort));
    }

    private void multicastGreetingMessage() {
        sendMulticastMessage(String.format("GREETING/%s/%d",
                uuid.toString(), tcpPort));
    }

    private void multicastLeavingMessage() {
        sendMulticastMessage(String.format("LEAVING/%s",
                uuid.toString()));
    }

    private void onTcpClientConnect(TcpSynchroServerSideClient connection){
        try {
            connection.setTimeout(TCP_TIMEOUT);
            Thread tcpConection = new Thread(()->{
                try {
                    String message = connection.getMessage();
                } catch (IOException e) {
                    System.out.println("Peer IO: " + e.getMessage());
                }
            });
        } catch (SocketException e) {
            System.out.println("Peer Socket: " + e.getMessage());
        }
    }

    private void sendPublicKey(PeerOpponent peer){

    }

    private synchronized void setIndexing(boolean indexing){
        this.indexing = indexing;
    }

    private synchronized void setIndexerUp(boolean indexerUp){
        this.indexerUp = indexerUp;
    }

    private synchronized void peerMapPut(UUID key, PeerOpponent value){
        peerMap.put(key, value);
    }

    private synchronized PeerOpponent peerMapRemove(UUID key){
        return peerMap.remove(key);
    }

    private void delay(int n){
        try {
            Thread.sleep(n*DELTA);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void delay(){
        delay(1);
    }
}
