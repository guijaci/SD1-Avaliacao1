package edu.utfpr.guilhermej.sisdist.av1.model;

import edu.utfpr.guilhermej.sisdist.av1.listener.IMessageEventListener;
import edu.utfpr.guilhermej.sisdist.av1.listener.INetMessageEventListener;
import edu.utfpr.guilhermej.sisdist.av1.listener.ITriggerEventListener;
import edu.utfpr.guilhermej.sisdist.av1.network.*;
import edu.utfpr.guilhermej.sisdist.av1.util.Pair;

import javax.crypto.*;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public class Peer {
    public static final int TCP_TIMEOUT = 5000;
    public static final int MIN_INDEXER_PEERS = 1;
    public static final int RECONNECTION_TRIES = 3;
    //Constantes para criptografia
    private static final int ASSYMETRIC_KEY_LENGTH = 1024;
    private static final String ASSYMETRIC_ALGORITHM = "RSA";
    private static final int SYMETRIC_KEY_LENGTH =  256;
    private static final String SYMETRIC_ALGORITHM = "AES";
    //IP multicast
    private static final String MULTICAST_IP_ADD = "233.32.31.30";
    //Intervalo de tempo em que um indexador envia mensagens
    private static final int DELTA = 2500;
    public static final float INITIAL_MONEY = 1000f;

    private UUID uuid;
    private KeyPair keyPair;
    private MulticastPeer multicastPeer;
    private TcpServer tcpServer;

    private PeerOpponent lastActiveIndexer;
    private final Map<UUID, PeerOpponent> peerMap;
    private final List<SaleItem> saleItemList;

    private List<IMessageEventListener> messageEventListeners;
    private List<ITriggerEventListener> indexerConnectionEventListeners;

    private float money = INITIAL_MONEY;
    private int tcpPort;
    private boolean executionEnable = false;
    private boolean indexerClockFlag = false;
    private boolean indexerUp = false;
    private boolean indexing = false;

    private final Object indexerUpLock;

    public Peer(){
        peerMap = new HashMap<>();
        saleItemList = new ArrayList<>();

        messageEventListeners = new ArrayList<>();
        indexerConnectionEventListeners = new ArrayList();

        indexerUpLock = new Object();

        uuid = UUID.randomUUID();
        keyPair = buildKeyPair(ASSYMETRIC_KEY_LENGTH, ASSYMETRIC_ALGORITHM);
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
                .setUuid(uuid)
                .setKey(keyPair.getPublic()));

        executionEnable = true;
        initIndexerControllThread();
    }

    public void sendMulticastMessage(String message) {
        multicastPeer.sendMessage(message);
    }

    public String getId(){
        return uuid.toString();
    }

    public void addSaleItem(SaleItem item){
        synchronized (saleItemList) {
            saleItemList.add(item);
        }
        if(!indexing) {
            if (indexerUp)
                sendAddSaleItem(item, lastActiveIndexer);
        }
        else {
            synchronized (peerMap) {
                peerMap.get(uuid).addItem(item);
            }
        }
    }

    public void searchItemDescription(String description) {
        if(!indexerUp)
            return;
        if(!indexing){
            sendSearchItemByDescription(description, lastActiveIndexer);
        }
        else{
            Pair<PeerOpponent, SaleItem> pair = getPeerBySaleItemDescription(uuid, description);
            if(pair != null)
                sendBuyItem(pair.getRight(), pair.getLeft());
            //TODO: caso nao encontre nenhum item
            //else ...
        }

    }

    public void disconnect() {
        multicastLeavingMessage();
        executionEnable = false;
        tcpServer.disconnect();
        multicastPeer.disconect();
    }

    public void addMulticastMessageEventListener(IMessageEventListener messageListener){
        messageEventListeners.add(messageListener);
    }

    public void removeMulticatsMesageEventListener(INetMessageEventListener messageListener){
        messageEventListeners.remove(messageListener);
    }

    public void addIndexerConnectionEventListener(ITriggerEventListener listener){
        indexerConnectionEventListeners.add(listener);
    }

    public void removeIndexerConnectEventListener(ITriggerEventListener listener){
        indexerConnectionEventListeners.remove(listener);
    }

    @Override
    public String toString() {
        return String.format("Peer ID: [%s]- TCP Port: [%d]",uuid.toString(),tcpPort);
    }

    private void onMessageEvent(String message) {
        messageEventListeners.forEach(listener->listener.onMessageReceived(message));
    }

    private void onMessageEventAsync(String message) {
        Thread onMessageThread = new Thread(()->onMessageEvent(message));
        onMessageThread.setName("Asynchronous Message Event");
        onMessageThread.start();
    }

    private void onIndexerConnectionEvent(boolean connected){
        indexerConnectionEventListeners.forEach(listener->listener.onTriggerEvent(connected));
    }

    private void refreshIndexer(UUID indexerUuid) {
        synchronized (peerMap){
            setLastActiveIndexer(peerMap.get(indexerUuid));
        }
        if(!indexing) {
            sendKey(lastActiveIndexer, keyPair.getPublic());
            if (!saleItemList.isEmpty())
                sendAddSaleItemList(saleItemList, lastActiveIndexer);
        }
        else {
            synchronized (peerMap) {
                PeerOpponent indexerInMap = peerMap.get(indexerUuid);
                synchronized (saleItemList) {
                    saleItemList.forEach(indexerInMap::addItem);
                }
            }
        }
    }

    private KeyPair buildKeyPair(int length, String algorithm){
        try{
            KeyPairGenerator keyGen;
            keyGen = KeyPairGenerator.getInstance(algorithm);
            keyGen.initialize(length, new SecureRandom());
            return keyGen.generateKeyPair();
        }catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }
        return null;
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

    private void sendKey(PeerOpponent peer, Key key){
        Thread sendPublicKey = new Thread(()->{
            TcpSynchroClient connection = null;
            for(int i = 0; i < RECONNECTION_TRIES; i++) {
                try {
                    connection = new TcpSynchroClient(peer.getIpAddress(), peer.getPortTcp());
                    connection.setTimeout(TCP_TIMEOUT);
                    String publicKey = keyToHex(key);
                    tcpIntroduceMessage(connection, null);
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
                    tcpIntroduceMessage(connection, null);
                    synchronized (itemList) {
                        for (SaleItem item : itemList)
                            tcpAddMessage(connection, null, item);
                    }
                    tcpFinishMessage(connection, null);
                    String response = connection.getMessage();
                    if(processTcpMessage(response, connection, peer.getUuid(), null))
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
                    tcpIntroduceMessage(connection, null);
                    tcpAddMessage(connection, null, item);
                    tcpFinishMessage(connection, null);
                    String response = connection.getMessage();
                    if(processTcpMessage(response, connection, peer.getUuid(), null))
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

    private void sendSearchItemByDescription(String description, PeerOpponent peer){
        Thread sendSearchItemByDescriptionThread = new Thread(()-> {
            TcpSynchroClient connection = null;
            for(int i = 0; i < RECONNECTION_TRIES; i++) {
                try {
                    connection = new TcpSynchroClient(peer.getIpAddress(), peer.getPortTcp());
                    connection.setTimeout(TCP_TIMEOUT);
                    tcpIntroduceMessage(connection, null);
                    tcpSearchMessage(connection, null, description);
                    tcpFinishMessage(connection, null);
                    String response = connection.getMessage();
                    if(processTcpMessage(response, connection, peer.getUuid(), null))
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
        sendSearchItemByDescriptionThread.setName("TCP Client Send Search Item by Description");
        sendSearchItemByDescriptionThread.start();
    }

    private void sendBuyItem(SaleItem item, PeerOpponent peer){
        Thread sendBuyItem = new Thread(()-> {
            TcpSynchroClient connection = null;
                try {
                    connection = new TcpSynchroClient(peer.getIpAddress(), peer.getPortTcp());
                    connection.setTimeout(TCP_TIMEOUT);
                    tcpIntroduceMessage(connection, peer.getKey());
                    tcpBuyMessage(connection, peer.getKey(), item);
                    tcpFinishMessage(connection, peer.getKey());
                    String response = connection.getMessage();
                    if(processTcpMessage(response, connection, peer.getUuid(), null))
                        money -= item.getPrice();
                } catch (IOException e) {
                    System.out.println("Peer IO: " + e.getMessage());
                } finally {
                    if (connection != null)
                        connection.disconnect();
                    delay();
                }

        });
        sendBuyItem.setName("TCP Client Send Search Item by Description");
        sendBuyItem.start();
    }

    private void initIndexerControllThread(){
        Thread peerThread = new Thread(() -> {
            multicastGreetingMessage();
            delay();
            while(executionEnable){
                if(peerMap.size() > MIN_INDEXER_PEERS) {
                    if (indexing) {
                        setIndexerUp(true);
                        multicastIndexingMessage();
                    }
                    else {
                        if(indexerClockFlag){
                            setIndexerUp(true);
                            setIndexerClockFlag(false);
                            delay();
                        }
                        else{
                            List<PeerOpponent> peers = null;
                            synchronized (peerMap){
                                peers = new ArrayList<>(peerMap.values());
                            }
                            peers.sort(Comparator.comparing(PeerOpponent::getUuid));
                            if(uuid.equals(peers.get(0).getUuid())){
                                setIndexing(true);
                                refreshIndexer(peers.get(0).getUuid());
                                continue;
                            }
                            setIndexerUp(false);
                            delay(2);
                            if(!indexerClockFlag){
                                synchronized (peerMap) {
                                    peerMap.remove(peers.get(0).getUuid());
                                }
                                continue;
                            }
                        }
                    }
                }
                else {
                    setIndexerUp(false);
                    setIndexing(false);
                }
                delay();
            }
        });
        peerThread.setName("Indexer Controll Thread");
        peerThread.start();
    }

    private void processMulticastMessage(String message, InetAddress address){
        onMessageEvent("Multicast: " + message);
        String msgTokens[] = message.split("/");
        UUID senderUuid = UUID.fromString(msgTokens[1]);
        if(uuid.equals(senderUuid)) return;
        PeerOpponent newPeer = null;
        String messageType = msgTokens[0];
        switch (messageType){
            case "GREETING":
                multicastInviteMessage(senderUuid);
                synchronized (peerMap) {
                    if(!peerMap.containsKey(senderUuid)){
                        peerMap.put(senderUuid,
                                new PeerOpponent()
                                        .setUuid(senderUuid)
                                        .setIpAddress(address)
                                        .setPortTcp(Integer.parseInt(msgTokens[2])));
                    }
                }
                break;
            case "INDEXING":
                setIndexerClockFlag(true);
                setIndexing(false);
                newPeer = new PeerOpponent()
                        .setUuid(senderUuid)
                        .setIpAddress(address)
                        .setPortTcp(Integer.parseInt(msgTokens[2]));
                synchronized (peerMap) {
                    peerMap.values().forEach(PeerOpponent::clearItems);
                    if(!peerMap.containsKey(senderUuid))
                        peerMap.put(senderUuid, newPeer);
                }
                if(lastActiveIndexer == null) {
                    refreshIndexer(senderUuid);
                }
                else if(!senderUuid.equals(lastActiveIndexer.getUuid())) {
                    refreshIndexer(senderUuid);
                }
                break;
            case "INVITE":
                UUID invited = UUID.fromString(msgTokens[2]);
                if(uuid.equals(invited)) {
                    newPeer = new PeerOpponent()
                            .setIpAddress(address)
                            .setPortTcp(Integer.parseInt(msgTokens[3]))
                            .setUuid(senderUuid);
                    synchronized (peerMap) {
                        if(!peerMap.containsKey(senderUuid)) {
                            peerMap.put(senderUuid, newPeer);
                        }
                    }
                }
                break;
            case "LEAVING":
                if(peerMap.containsKey(senderUuid))
                    synchronized (peerMap) {
                        peerMap.remove(senderUuid);
                    }
                break;
            default:
        }
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
                                      Key encryptionKey)
            throws IOException {
        onMessageEvent("TCP: " + message);
        String[] msgTokens = message.split("/");
        String messageType = msgTokens[0];
        switch (messageType){
            case "ADD":
                if(senderUuid != null){
                    if (!indexing) {
                        tcpErrorMessage(connection, encryptionKey,
                                "Process is not indexer",
                                10);
                    }
                    SaleItem item = new SaleItem()
                            .setDescription(msgTokens[1])
                            .setPrice(Float.parseFloat(msgTokens[2]));
                    boolean failed = true;
                    synchronized (peerMap){
                        if (peerMap.containsKey(senderUuid)) {
                            peerMap.get(senderUuid).addItem(item);
                            failed = false;
                        }
                    }
                    if(failed)
                        tcpErrorMessage(connection, encryptionKey,
                                "Process don't know requester",
                                20);
                }
                else
                    tcpErrorMessage(connection,null,
                            "Process have not announced itself", 30);
                break;
            case "BUY":
                SaleItem wanted = new SaleItem()
                        .setDescription(msgTokens[1])
                        .setPrice(Float.parseFloat(msgTokens[2]));
                synchronized (saleItemList){
                    Optional<SaleItem> optional = saleItemList
                            .stream()
                            .filter(item -> item.getDescription().equals(wanted.getDescription()) &&
                                    Float.valueOf(item.getPrice()).equals(wanted.getPrice()))
                            .findFirst();
                    if(optional.isPresent()) {
                        optional.ifPresent(saleItemList::remove);
                        money += wanted.getPrice();
                    }
                    else
                        tcpErrorMessage(connection, encryptionKey, "Transaction refused", 60);
                }
                break;
            case "ENCRYPTED":
                try {
                    Cipher cipher = Cipher.getInstance(ASSYMETRIC_ALGORITHM);
                    cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
                    String decrypted = new String(cipher.doFinal(DatatypeConverter.parseHexBinary(msgTokens[1])), StandardCharsets.UTF_8);
                    processTcpMessage(decrypted, connection, senderUuid, encryptionKey);
                } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
                    e.printStackTrace();
                }
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
            case "FOUND":
                if(indexerUp && senderUuid != null && senderUuid.equals(lastActiveIndexer.getUuid())) {
                    if (msgTokens.length > 1) {
                        SaleItem saleItem = new SaleItem()
                                .setDescription(msgTokens[1])
                                .setPrice(Float.parseFloat(msgTokens[2]));
                        UUID sellerUuid = UUID.fromString(msgTokens[3]);
                        Key sellerKey = hexToKey(msgTokens[4]);
                        boolean failed = true;
                        synchronized (peerMap){
                            if(peerMap.containsKey(sellerUuid)) {
                                PeerOpponent seller = peerMap.get(sellerUuid)
                                        .setKey(sellerKey);
                                sendBuyItem(saleItem, seller);
                                failed = false;
                            }
                        }
                        if(failed)
                            tcpErrorMessage(connection,null, String.format("Identifier \"%s\" not known by peer", uuid.toString()),50);
                    }
                    //TODO: quando nenhum item foi encontrado
                    //else
                        //item not found
                }
                else
                    tcpErrorMessage(connection,null, "Client have not requested search", 40);
                break;
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
                    String hexString = msgTokens[1];
                    Key publicKey = hexToKey(hexString);
                    boolean failed = true;
                    synchronized (peerMap){
                        if (peerMap.containsKey(senderUuid)) {
                            peerMap.get(senderUuid).setKey(publicKey);
                            tcpOkMessage(connection, encryptionKey);
                            failed = false;
                        }
                    }
                    if(failed)
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
            case "SEARCH":
                if(senderUuid != null) {
                    if (!indexing) {
                        tcpErrorMessage(connection, encryptionKey,
                                "Process is not indexer",
                                10);
                    }
                    Pair<PeerOpponent, SaleItem> pair = getPeerBySaleItemDescription(senderUuid, msgTokens[1]);
                    if(pair != null && pair.getLeft() != null && pair.getLeft().getKey() != null) {
                        PeerOpponent peer = pair.getLeft();
                        String key = keyToHex(peer.getKey());
                        SaleItem item = pair.getRight();
                        tcpFoundMessage(connection, encryptionKey, peer, item, key);
                    }
                    else
                        connection.sendMessage("FOUND");
                }
                else
                    tcpErrorMessage(connection,null,
                            "Process have not announced itself", 30);
                break;
            default:
                throw new IOException("Unknown Message");
        }
        return processTcpMessage(connection.getMessage(), connection, senderUuid, encryptionKey);
    }

    private void tcpFoundMessage(ISocketConnection connection,
                                 Key key,
                                 PeerOpponent peer,
                                 SaleItem item,
                                 String peerKey)
            throws IOException {
        String message = String.format("FOUND/%s/%.02f/%s/%s", item.getDescription(), item.getPrice(), peer.getUuid(), peerKey);
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    private void tcpIntroduceMessage(ISocketConnection connection,
                                     Key key)
            throws IOException {
        String message = String.format("INTRODUCE/%s", uuid.toString());
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    private void tcpKeyMessage(ISocketConnection connection,
                               String key,
                               Key encryptionKey)
            throws IOException {
        String message = String.format("KEY/%s", key);
        if(encryptionKey != null)
            tcpEncryptedMessage(connection, encryptionKey, message);
        else
            connection.sendMessage(message);
    }

    private void tcpAddMessage(ISocketConnection connection,
                               Key key,
                               SaleItem item)
            throws IOException {
        String message = String.format("ADD/%s/%.2f", item.getDescription(), item.getPrice());
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    private void tcpOkMessage(ISocketConnection connection,
                              Key key)
            throws IOException {
        String message = String.format("OK");
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    private void tcpFinishMessage(ISocketConnection connection,
                                  Key key)
            throws IOException {
        String message = String.format("FINISH");
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    private void tcpErrorMessage(ISocketConnection connection,
                                 Key key,
                                 String errorMessage,
                                 int errorCode)
            throws IOException {
        String message = String.format("ERROR/%d/%s", errorCode, errorMessage);
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    private void tcpSearchMessage(ISocketConnection connection,
                                  Key key,
                                  String description)
            throws IOException {
        String message = String.format("SEARCH/%s", description);
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    private void tcpBuyMessage(ISocketConnection connection,
                               Key key,
                               SaleItem item)
            throws IOException {
        String message = String.format("BUY/%s/%.02f", item.getDescription(), item.getPrice());
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    private void tcpEncryptedMessage(ISocketConnection connection,
                                     Key key,
                                     String message)
        throws IOException{
        if(key != null) {
            try {
                Cipher cipher = Cipher.getInstance(ASSYMETRIC_ALGORITHM);
                cipher.init(Cipher.ENCRYPT_MODE, key);
                String encrypted = DatatypeConverter.printHexBinary(cipher.doFinal(message.getBytes(StandardCharsets.UTF_8)));
                connection.sendMessage(String.format("ENCRYPTED/%s",encrypted));
            } catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | InvalidKeyException | NoSuchPaddingException e) {
                e.printStackTrace();
            }
        }
    }

    private Pair<PeerOpponent, SaleItem> getPeerBySaleItemDescription(UUID requester, String saleItemDescription){
        synchronized (peerMap) {
            if(!peerMap.containsKey(requester))
                return null;

            return peerMap
                    .entrySet()
                    .parallelStream()
                    .filter( pair -> !requester.equals(pair.getKey()) )
                    .flatMap(
                            pair ->
                            pair.getValue().getItems(saleItemDescription)
                            .parallelStream()
                            .map( item -> new Pair<>(pair.getValue(), item))
                    )
                    .min(
                            (o1, o2) ->
                            Float.valueOf(o1.getRight().getPrice()).equals(o2.getRight().getPrice()) ?
                            Integer.compare(o1.getLeft().getReputation(), o1.getLeft().getReputation()) :
                            Float.compare(o1.getRight().getPrice(), o2.getRight().getPrice() )
                    )
                    .orElse( null );
        }
    }

    private String keyToHex(Key key) {
        return DatatypeConverter.printHexBinary(key.getEncoded());
    }

    private Key hexToKey(String hex){
        try {
            return KeyFactory.getInstance(ASSYMETRIC_ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(DatatypeConverter.parseHexBinary(hex)));
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(String.format("Expected valid string of printed bytes for key generation (found: %s)",hex), e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Expected "+ASSYMETRIC_ALGORITHM+" algorithm for encryption", e);
        }
    }

    private void setIndexerUp(boolean indexerUp){
        if(this.indexerUp != indexerUp)
            onIndexerConnectionEvent(indexerUp);
        synchronized (indexerUpLock) {
            this.indexerUp = indexerUp;
        }
    }

    private synchronized void setIndexing(boolean indexing){
        this.indexing = indexing;
    }

    private synchronized void setIndexerClockFlag(boolean indexerClockFlag){
        this.indexerClockFlag = indexerClockFlag;
    }

    private synchronized void setLastActiveIndexer(PeerOpponent lastActiveIndexer) {
        this.lastActiveIndexer = lastActiveIndexer;
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
