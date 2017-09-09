package edu.utfpr.guilhermej.sisdist.av1.model;

import edu.utfpr.guilhermej.sisdist.av1.listener.IMessageEventListener;
import edu.utfpr.guilhermej.sisdist.av1.listener.INetMessageEventListener;
import edu.utfpr.guilhermej.sisdist.av1.network.*;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public class Peer {
    public static final int TCP_TIMEOUT = 5000;
    public static final int MIN_INDEXER_PEERS = 3;
    public static final int RECONNECTION_TRIES = 3;
    //Constantes para criptografia
    private static final int KEY_LENGTH = 512;
    private static final String ENCRYPTION_ALGORITHM = "RSA";
    //IP multicast
    private static final String MULTICAST_IP_ADD = "233.32.31.30";
    //Intervalo de tempo em que um indexador envia mensagens
    private static final int DELTA = 2500;

    private UUID uuid;
    private KeyPair keyPair;
    private MulticastPeer multicastPeer;
    private TcpServer tcpServer;

    private PeerOpponent lastActiveIndexer;
    private Map<UUID, PeerOpponent> peerMap;
    private List<SaleItem> saleItemList;

    private List<IMessageEventListener> messageListeners;

    private int tcpPort;
    private boolean executionEnable = false;
    private boolean indexerWatch = false;
    private boolean indexerUp = false;
    private boolean indexing = false;

    public Peer(){
        peerMap = new HashMap<>();
        saleItemList = new ArrayList<>();
        messageListeners = new ArrayList<>();

        uuid = UUID.randomUUID();
        keyPair = buildKeyPair(KEY_LENGTH, ENCRYPTION_ALGORITHM);
        multicastPeer = new MulticastPeer(MULTICAST_IP_ADD);
        multicastPeer.addMessageListener(this::processMulticastMessage);

        tcpPort = 60000 + new Random().nextInt(5535);
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
        initIndexerControllThread();
    }

    public void sendMulticastMessage(String message) {
        multicastPeer.sendMessage(message);
    }

    public String getId(){
        return uuid.toString().toString();
    }

    public void addSaleItem(SaleItem item){
        saleItemList.add(item);
        if(indexerUp)
            sendAddSaleItem(item, lastActiveIndexer);
    }

    public void disconnect() {
        multicastLeavingMessage();
        executionEnable = false;
        tcpServer.disconnect();
        multicastPeer.disconect();
    }

    public void addMulticastMessageListener(IMessageEventListener messageListener){
        messageListeners.add(messageListener);
    }

    public void removeMulticatsMesageListener(INetMessageEventListener messageListener){
        messageListeners.remove(messageListener);
    }

    @Override
    public String toString() {
        return String.format("Peer ID: [%s]- TCP Port: [%d]",uuid.toString(),tcpPort);
    }

    private void refreshIndexer(PeerOpponent peer) {
        lastActiveIndexer = peer;
        sendPublicKey(lastActiveIndexer);
        if(!saleItemList.isEmpty())
            sendAddSaleItemList(saleItemList, lastActiveIndexer);
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

    private void onTcpClientConnect(TcpSynchroServerSideClient connection){
        try {
            connection.setTimeout(TCP_TIMEOUT);
            Thread tcpConnection = new Thread(()->{
                try {
                    String message = connection.getMessage();
                    processTcpMessage(message, connection, null,null);
                } catch (IOException e) {
                    System.out.println("Peer IO: " + e.getMessage());
                }finally {
                    connection.disconnect();
                }
            });
            tcpConnection.setName("TCP Server Side Client Connection");
            tcpConnection.start();
        } catch (SocketException e) {
            System.out.println("Peer Socket: " + e.getMessage());
        }
    }

    private void sendPublicKey(PeerOpponent peer){
        Thread sendPublicKey = new Thread(()->{
            TcpSynchroClient connection = null;
            for(int i = 0; i < RECONNECTION_TRIES; i++) {
                try {
                    connection = new TcpSynchroClient(peer.getIpAddress(), peer.getPortTcp());
                    connection.setTimeout(TCP_TIMEOUT);
                    String publicKey = keyToHex(keyPair.getPublic());
                    tcpIntroduceMessage(connection, uuid, null);
                    tcpKeyMessage(connection, publicKey, null);
                    tcpFinishMessage(connection, null);
                    String response = connection.getMessage();
                    if(processTcpMessage(response, connection, null, null))
                        break;
                } catch (IOException e) {
                    System.out.println("Peer IO: " + e.getMessage());
                } finally {
                    if (connection != null)
                        connection.disconnect();
                    delay();
                }
            }
        });
        sendPublicKey.setName("TCP Client Send Public Key");
        sendPublicKey.start();
    }

    private void sendAddSaleItemList(List<SaleItem> itemList, PeerOpponent peer){
        Thread sendSaleItemList = new Thread(()-> {
            TcpSynchroClient connection = null;
            for(int i = 0; i < RECONNECTION_TRIES; i++) {
                try {
                    connection = new TcpSynchroClient(peer.getIpAddress(), peer.getPortTcp());
                    connection.setTimeout(TCP_TIMEOUT);
                    String publicKey = keyToHex(keyPair.getPublic());
                    tcpIntroduceMessage(connection, uuid, null);
                    for(SaleItem item: itemList)
                        tcpAddMessage(connection, null, item);
                    tcpFinishMessage(connection, null);
                    String response = connection.getMessage();
                    if(processTcpMessage(response, connection, null, null))
                        break;
                } catch (IOException e) {
                    System.out.println("Peer IO: " + e.getMessage());
                } finally {
                    if (connection != null)
                        connection.disconnect();
                    delay();
                }
            }
        });
        sendSaleItemList.setName("TCP Client Send Sale Item List");
        sendSaleItemList.start();
    }

    private void sendAddSaleItem(SaleItem item, PeerOpponent peer){
        Thread sendSaleItemList = new Thread(()-> {
            TcpSynchroClient connection = null;
            for(int i = 0; i < RECONNECTION_TRIES; i++) {
                try {
                    connection = new TcpSynchroClient(peer.getIpAddress(), peer.getPortTcp());
                    connection.setTimeout(TCP_TIMEOUT);
                    String publicKey = keyToHex(keyPair.getPublic());
                    tcpIntroduceMessage(connection, uuid, null);
                    tcpAddMessage(connection, null, item);
                    tcpFinishMessage(connection, null);
                    String response = connection.getMessage();
                    if(processTcpMessage(response, connection, null, null))
                        break;
                } catch (IOException e) {
                    System.out.println("Peer IO: " + e.getMessage());
                } finally {
                    if (connection != null)
                        connection.disconnect();
                    delay();
                }
            }
        });
        sendSaleItemList.setName("TCP Client Send Sale Item");
        sendSaleItemList.start();
    }

    private void initIndexerControllThread(){
        Thread peerThread = new Thread(() -> {
            multicastGreetingMessage();
            delay();
            while(executionEnable){
                if(peerMap.size() > MIN_INDEXER_PEERS) {
                    if (indexing)
                        multicastIndexingMessage();
                    else {
                        if(indexerWatch){
                            setIndexerUp(true);
                            setIndexerWatch(false);
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
                            setIndexerUp(false);
                            delay(2);
                            if(!indexerWatch){
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
        peerThread.setName("Indexer Controll Thread");
        peerThread.start();
    }

    private void processMulticastMessage(String message, InetAddress address){
        String msgTokens[] = message.split("/");
        UUID senderUuid = UUID.fromString(msgTokens[1]);
        if(uuid.equals(senderUuid)) return;
        String messageType = msgTokens[0];
        switch (messageType){
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
                setIndexerWatch(true);
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
                        refreshIndexer(peerMap.get(senderUuid));
                    }
                }
                else
                    refreshIndexer(peerMap.get(senderUuid));
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

    private boolean processTcpMessage(String message,
                                      ISocketConnection connection,
                                      UUID senderUuid,
                                      Key encryptionKey) throws IOException {
        String msg = message;
        String[] msgTokens = msg.split("/");
        String messageType = msgTokens[0];
        switch (messageType){
            case "ADD":
                if(senderUuid != null){
                    if (!indexing) {
                        tcpErrorMessage(connection, encryptionKey,
                                "Process is not indexer",
                                10);
                    }
                    if (peerMap.containsKey(senderUuid)) {
                        SaleItem item = new SaleItem()
                                .setItemName(msgTokens[1])
                                .setPrice(Float.parseFloat(msgTokens[2]))
                                .setQuantity(Integer.parseInt(msgTokens[3]));
                        peerMap.get(senderUuid).addItem(item);
                    } else
                        tcpErrorMessage(connection, encryptionKey,
                                "Process don't know requester",
                                20);
                }
                else
                    tcpErrorMessage(connection,null,
                            "Process have not announced itself", 30);
                break;
            case "ERROR":
                String errorMsg = msgTokens[2];
                int errorCode = Integer.parseInt(msgTokens[1]);
                String e;
                if(senderUuid != null)
                    e = String.format("ERROR %d: %s send \"%s\"", errorCode, senderUuid.toString(), errorMsg);
                else
                    e = String.format("ERROR %d: %s", errorCode, errorMsg);
                System.out.println(e);
                throw new IOException(e);
            case "FINISH":
                tcpOkMessage(connection, encryptionKey);
                return true;
            case "INTRODUCE":
                senderUuid = UUID.fromString(msgTokens[1]);
                break;
            case "KEY":
                if(senderUuid != null) {
                    if (!indexing) {
                        tcpErrorMessage(connection, encryptionKey,
                                "Process is not indexer",
                                10);
                    }
                    if (peerMap.containsKey(senderUuid)) {
                        PeerOpponent peer = peerMap.get(senderUuid);
                        String hexString = msgTokens[1];
                        Key publicKey = hexToKey(hexString);
                        peer.setKey(publicKey);
                        tcpOkMessage(connection, encryptionKey);
                    } else
                        tcpErrorMessage(connection, encryptionKey,
                                "Process don't know requester",
                                20);
                }
                else
                    tcpErrorMessage(connection,null,
                            "Process have not announced itself", 30);
                break;
            case "OK":
                return true;
            default:
                throw new IOException("Unknown Message");
        }
        return processTcpMessage(connection.getMessage(), connection, senderUuid, encryptionKey);
    }

    private void tcpIntroduceMessage(TcpSynchroClient connection,
                                     UUID fromUuid,
                                     Key key)
            throws IOException {
        connection.sendMessage(String.format("INTRODUCE/%s", fromUuid.toString()));
    }

    private void tcpKeyMessage(TcpSynchroClient connection,
                               String key,
                               Key encryptionKey)
            throws IOException {
        String message = String.format("KEY/%s", key);
        connection.sendMessage(message);
    }

    private void tcpAddMessage(TcpSynchroClient connection,
                               Key key,
                               SaleItem item)
            throws IOException {
        connection.sendMessage(String.format("ADD/%s/%.2f/%d", item.getItemName(), item.getPrice(), item.getQuantity()));
    }

    private void tcpOkMessage(ISocketConnection connection,
                              Key key)
            throws IOException {
        String message = String.format("OK");
        connection.sendMessage(message);
    }

    private void tcpFinishMessage(ISocketConnection connection,
                                  Key key)
            throws IOException {
        String message = String.format("FINISH");
        connection.sendMessage(message);
    }

    private void tcpErrorMessage(ISocketConnection connection,
                                 Key key,
                                 String errorMessage,
                                 int errorCode)
            throws IOException {
        String message = String.format("ERROR/%d/%s", errorCode, errorMessage);
        connection.sendMessage(message);
    }

    private String keyToHex(Key key) {
        return DatatypeConverter.printHexBinary(key.getEncoded());
    }

    private Key hexToKey(String hex){
        try {
            return KeyFactory.getInstance(ENCRYPTION_ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(DatatypeConverter.parseHexBinary(hex)));
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(String.format("Expected valid string of printed bytes for key generation (found: %s)",hex), e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Expected "+ENCRYPTION_ALGORITHM+" algorithm for encryption", e);
        }
    }

    private synchronized void setIndexing(boolean indexing){
        this.indexing = indexing;
    }

    private synchronized void setIndexerWatch(boolean indexerWatch){
        this.indexerWatch = indexerWatch;
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

    private static void delay(int n){
        try {
            Thread.sleep(n*DELTA);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void delay(){
        delay(1);
    }
}
