package edu.utfpr.guilhermej.sisdist.av1.model;

import edu.utfpr.guilhermej.sisdist.av1.event.ItemListEvent;
import edu.utfpr.guilhermej.sisdist.av1.event.ItemProposalEvent;
import edu.utfpr.guilhermej.sisdist.av1.listener.*;
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
import java.util.stream.Collectors;

/**
 * Esta classe representa o participante no processo, e seus objetos se
 * comunicam através de Sockets mesmo em outros processos.
 * (Esta classe deveria ter sido refatorada em mais umas 3, mas não tive tempo de fazê-lo.
 * Utilize "dobragem" de código - code folding - da IDE para ficar mais fácil percorrê-la)
 * Obs.: Conexões Multicast aqui são assíncronas e funcionam com Observadores, já
 * as conexões unicast são síncronas e, portanto, bloqueantes. Isto foi feito para ser mais fácil
 * capturar excessões de conexão unicast.
 * @author Guilherme
 */
public class Peer {
    //<editor-fold desc="Constantes">
    /** Timeout durante comunicação síncrona, para evitar falhas de comunicação. */
    private static final int TCP_TIMEOUT = 5000;
    /** Minimo de pares necessário para iniciar eleição do indexador. */
    private static final int MIN_INDEXER_PEERS = 3;
    /**  Numero de tentativas para reconexão depois de haver falha */
    private static final int RECONNECTION_TRIES = 3;
    /** Tamanho da chave criptografica utilizada */
    private static final int KEY_LENGTH = 1024;
    /** Algoritmo de criptografia (assimetrica) */
    private static final String CRYPTO_ALGORITHM = "RSA";
    /** IP multicast */
    private static final String MULTICAST_IP_ADD = "233.32.31.30";
    /** Intervalo de tempo em que um indexador envia mensagens */
    private static final int DELTA = 2500;
    /** Valor inicial de dinheiro {@link #money}*/
    public static final float INITIAL_MONEY = 1000f;
    //</editor-fold>

    //<editor-fold desc="Objetos Membro">
    /** Identificador do processo */
    private UUID uuid;
    /** Par de chaves assimetricas para criptografia */
    private KeyPair keyPair;
    /** Representa uma conexão multicast do processo */
    private MulticastPeer multicastPeer;
    /** Representa um servidor unicast do processo */
    private TcpServer tcpServer;

    /** Ultimo processo que atuou como indexador é armazenado aqui */
    private PeerOpponent lastActiveIndexer;
    /** Mapa de identificadores para respectivos pares */
    private final Map<UUID, PeerOpponent> peerMap;
    /** Lista de items para venda por esse processo */
    private final List<SaleItem> saleItemList;
    //</editor-fold>

    //<editor-fold desc="Lista de Observadores">
    // Listas de observadores para implementação de padrão de projeto Observador
    // ("Observer"), para desacoplar modelo e visão (janela)
    /** Observadores de eventos de mensagem (unicast e multicast) */
    private final List<IMessageEventListener> messageEventListeners;
    /** Observadores de eventos de conexão de indexador (quando se conecta com um indexador ou desconecta) */
    private final List<ITriggerEventListener> indexerConnectionEventListeners;
    /** Observadores de eventos de alteração do dinheiro */
    private final List<IFloatEventListener> moneyEventListener;
    /** Observadores de eventos de transação de items (procura, compra e venda) */
    private final List<IItemProposalEventListener> itemProposalEventListeners;
    /** Observadores de eventos de items adicionados, removidos ou alterados*/
    private final List<IItemListEventListener> itemListEventListeners;
    //</editor-fold>

    //<editor-fold desc="Variaveis membro">
    /** Dinheiro total do par */
    private float money = INITIAL_MONEY;
    /** Porta TCP do servidor unicast */
    private int tcpPort;
    /** Indica finalização da classe (utilizado para sair corretamente de threads que estão em loop) */
    private boolean executionEnable = false;
    /**
     *  Flag de disponibilidade do indexador. O processo baixa esta flag e dorme por DELTA ms.
     *  Quando recebe uma mensagem de disponibidade do indexador, ele a levanta novamente.
     *  Se a flag estiver baixa ao acordar, significa que o indexador caiu
     */
    private boolean indexerAnounced = false;
    /** Flag indica que indexador esta disponível */
    private boolean indexerUp = false;
    /** Flag indica que processo atual é o indexador*/
    private boolean indexing = false;
    //</editor-fold>

    //<editor-fold desc="Trancas de sincronização">
    /** Tranca de acesso à {@link #indexerUp} */
    private final Object indexerUpLock;
    /** Tranca de acesso à {@link #money}*/
    private final Object moneyLock;
    //</editor-fold>

    /**
     * Construtor padrão, inicializa objetos e variaveis membros.
     * Par Multicast e Servidor TCP são abertos ao construir objeto.
     * Inicia Thread de controle de indexação (envia mensagens de disponibilidade
     * quando é o indexador, e controla necessidade de eleição caso contrário).
     */
    public Peer(){
        //<editor-fold desc="Inicialização de listas, mapas e trancas">
        peerMap = new HashMap<>();
        saleItemList = new ArrayList<>();

        indexerUpLock = new Object();
        moneyLock = new Object();
        //</editor-fold>

        //<editor-fold desc="Inicialização Observadores">
        messageEventListeners = new ArrayList<>();
        indexerConnectionEventListeners = new ArrayList<>();
        moneyEventListener = new ArrayList<>();
        itemProposalEventListeners = new ArrayList<>();
        itemListEventListeners = new ArrayList<>();
        //</editor-fold>

        //Gerado novo identificador universal aleatório
        uuid = UUID.randomUUID();
        keyPair = buildKeyPair(KEY_LENGTH, CRYPTO_ALGORITHM);
        multicastPeer = new MulticastPeer(MULTICAST_IP_ADD);
        //Adiciona função de processamento de mensagens multicast como observador de mensagens do par multicast
        multicastPeer.addMessageListener(this::processMulticastMessage);

        //Porta TCP é um inteiro entre 60000 e 65535
        tcpPort = 60000 + new Random().nextInt(5535);
        try{
            tcpServer = new TcpServer(tcpPort);
        }catch (IOException e){
            e.printStackTrace();
        }
        //Adiciona função como observador de conexões de clientes do servidor TCP.
        //Este observador criará uma thread que receberá e processará as requisições do novo cliente
        tcpServer.addTcpConnectionListener(this::onTcpClientConnect);

        //Adiciona a si mesmo na lista de pares conhecidos
        peerMap.put(uuid,
                new PeerOpponent()
                .setPortTcp(tcpPort)
                .setUuid(uuid)
                .setKey(keyPair.getPublic()));

        //habilita sua propria conexão
        executionEnable = true;
        //Inicia thread de controle de indexação e eleição
        initIndexerControlThread();
    }

    //<editor-fold desc="Getters & Setters">
    /**
     * Retorna identificador deste par
     * @return identificador deste par
     */
    public String getId(){
        return uuid.toString();
    }

    /**
     * Retorna dinheiro atual do par
     * @return dinheiro atual do par
     */
    public float getMoney() {
        return money;
    }

    /**
     * Armazena novo valor de dinheiro.
     * Caso seja um valor diferente do anterior, lança evento para observadores.
     * @param money dinheiro
     * @return este objeto (para construção encadeada)
     */
    public Peer setMoney(float money) {
        if(this.money != money) {
            this.money = money;
            onMoneyEventAsync(money);
        }
        return this;
    }
    //</editor-fold>


    //<editor-fold desc="Funcionalidades para objetos Controladores">
    /**
     * Envia nova mensagem por multicast
     * @param message mensagem à ser enviada
     */
    public void sendMulticastMessage(String message) {
        multicastPeer.sendMessage(message);
    }

    /**
     * Adiciona novo item à lista de items para venda
     * @param item item adicionado
     */
    public void addSaleItem(SaleItem item){
        //Primeiramente adiciona item à lista local, e envia um evento de item criado
        synchronized (saleItemList) {
            saleItemList.add(item);
            onItemListEventAsync(new ItemListEvent(item, ItemListEvent.ItemListEventType.ADDED));
        }
        //Se não for indexador, envia mensagem unicast para atualizar lista do indexador
        if(!indexing) {
            if (indexerUp)
                sendAddSaleItem(item, lastActiveIndexer);
        }
        //Se for indexador, apenas adciona item ao seu objeto no mapa de pares
        else {
            synchronized (peerMap) {
                peerMap.get(uuid).addItem(item);
            }
        }
    }

    /**
     * Procura novo item com indexador para compra por descrição
     * @param description descrição do item que se deseja
     */
    public void searchItemDescription(String description) {
        //Não deve ser executado caso não exista indexador ativo
        if(!indexerUp)
            return;
        //Se não for indexador, envia mensagem unicast para indexador para pesquisa
        if(!indexing){
            sendSearchItemByDescription(description, lastActiveIndexer);
        }
        //Se for, realiza busca localmente e lança evento de transação de item (item encontrado/não encontrado)
        else{
            Pair<PeerOpponent, SaleItem> pair = getPairPeerItemByPriceAndReputation(getPeerBySaleItemDescription(uuid, description));
            if(pair != null)
                onItemProposalEventAsync(ItemProposalEvent.itemFound(pair.getRight(), pair.getLeft(), this::sendBuyItem));
            else
                onItemProposalEventAsync(ItemProposalEvent.itemNotFound(new SaleItem().setDescription(description)));
        }

    }

    /**
     *  Encerra threads interna e fecha conexões de rede
     */
    public void disconnect() {
        multicastLeavingMessage();
        executionEnable = false;
        tcpServer.disconnect();
        multicastPeer.disconect();
    }
    //</editor-fold>

    //<editor-fold desc="Adição e remoção de Observadores">
    /** Adiciona observador de mensagem */
    public void addMulticastMessageEventListener(IMessageEventListener messageListener){
        messageEventListeners.add(messageListener);
    }

    /** Remove observador de mensagem */
    public void removeMulticatsMesageEventListener(INetMessageEventListener messageListener){
        messageEventListeners.remove(messageListener);
    }

    /** Adiciona observador de conexão entre indexador */
    public void addIndexerConnectionEventListener(ITriggerEventListener listener){
        indexerConnectionEventListeners.add(listener);
    }

    /** Remove observador de conexão entre indexador */
    public void removeIndexerConnectEventListener(ITriggerEventListener listener){
        indexerConnectionEventListeners.remove(listener);
    }

    /** Adiciona observador de alteração no dinheiro */
    public void addMoneyListener(IFloatEventListener listener){
        moneyEventListener.add(listener);
    }

    /** Remove observador de alteração no dinheiro */
    public void removeMoneyListener(IFloatEventListener listener){
        moneyEventListener.remove(listener);
    }

    /** Adiciona observador de eventos relacionado a transação de items */
    public void addItemProposalEventListener(IItemProposalEventListener listener){
        itemProposalEventListeners.add(listener);
    }

    /** Remove observador de eventos relacionado a transação de items */
    public void removeItemProposalEventListener(IItemProposalEventListener listener){
        itemProposalEventListeners.remove(listener);
    }

    /** Adiciona observador adição, remoção e alteração de um item na lista */
    public void addItemListEventListener(IItemListEventListener listener){
        itemListEventListeners.add(listener);
    }

    /** Remove observador adição, remoção e alteração de um item na lista */
    public void removeItemListEventListener(IItemListEventListener listener){
        itemListEventListeners.remove(listener);
    }
    //</editor-fold>

    /**
     * Retorna uma representação em String desse objeto.
     * Utilizado para identificar mais facilmente o respectivo processo (é o titulo da janela)
     * @return representação em String desse objeto (ID + Porta TCP)
     */
    @Override
    public String toString() {
        return String.format("Peer ID: [%s]- TCP Port: [%d]",uuid.toString(),tcpPort);
    }

    //Inicio de funções privadas

    /**
     * Cria um par de chaves assimétricas aleatório
     * @param length tamanho das chaves em bits
     * @param algorithm algoritmo utilizado para geração de chaves
     * @return um par de chaves assimétricas
     */
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

    /**
     * Inicializa thread de controle do indexador e eleição
     */
    private void initIndexerControlThread(){
        Thread peerThread = new Thread(() -> {
            //Envia mensagem introdutória à todos os participantes
            multicastGreetingMessage();
            delay();
            //Enquanto não desconectar objeto, executa controle de indexador/ eleição
            while(executionEnable){
                //Caso número mínimo de pares tenha sido atingido ...
                if(peerMap.size() > MIN_INDEXER_PEERS) {
                    //... verifica se este objeto é o indexador ...
                    if (indexing) {
                        //... se for, anuncia sua disponibilidade ...
                        multicastIndexingMessage();
                        // ... e indica que indexador esta ativo ...
                        if(!indexerUp)
                            setIndexerUp(true);
                    }
                    else {
                        //... se não for o indexador, verifica se um indexador anunciou à si
                        // (pela flag #indexerAnounced) enquanto este objeto esperava ...
                        if(indexerAnounced){
                            //... se anunciou, reinicia flag e espera ela ser
                            // modificada novamente em novo anuncio de indexador ...
                            setIndexerAnounced(false);
                            // ... indicando que indexador esta ativo ...
                            if(!indexerUp)
                                setIndexerUp(true);
                            delay();
                        }
                        else{
                            //... se um indexador nao se anunciou, inicia processo de eleição ...
                            List<PeerOpponent> peers = null;
                            synchronized (peerMap){
                                peers = new ArrayList<>(peerMap.values());
                            }
                            //... com a lista de processos conhecidos ordenados por ID ...
                            peers.sort(Comparator.comparing(PeerOpponent::getUuid));
                            //... recupera primeiro da lista e verifica se é o objeto atual ...
                            if(uuid.equals(peers.get(0).getUuid())){
                                //... se for, este objeto se torna novo indexador ...
                                setIndexing(true);
                                //... realizando mudanças necessárias para novo indexador ...
                                refreshIndexer(peers.get(0).getUuid());
                                //... e reinicia o ciclo antecipadamente, para evitar espera desnecessária
                                // antes de se anunciar como disponível.
                                continue;
                            }
                            else {
                                //... se não for o próximo indexador escolhido, indica que não existe nenhum
                                // indexador ativo ...
                                if (indexerUp)
                                    setIndexerUp(false);
                                //... e espera por mais tempo até que alguém se anuncie ...
                                delay(2);
                                //... se ninguem se anunciar, retira o primeiro processo da lista,
                                // pois ele deve ter perdido conexão ...
                                if (!indexerAnounced) {
                                    synchronized (peerMap) {
                                        peerMap.remove(peers.get(0).getUuid());
                                    }
                                    //... e reinicia o ciclo antecipadamente para evitar esperas desnecessárias.
                                    continue;
                                }
                            }
                        }
                    }
                }
                else {
                    //... se não existe um mínimo de processos, não existe indexador nem este objeto esta indexando ...
                    if(indexerUp)
                        setIndexerUp(false);
                    if(indexing)
                        setIndexing(false);
                }
                //... e, por fim, espera um indexador se anunciar novamente
                delay();
            }
        });
        peerThread.setName("Indexer Control Thread");
        peerThread.start();
    }

    /**
     * Método atualiza estado interno quando novo indexador é encontrado,
     * enviando a chave deste processo, sua porta para conexão unicast e
     * enviando sua lista de items à venda.
     * @param indexerUuid identificador do novo indexador
     */
    private void refreshIndexer(UUID indexerUuid) {
        synchronized (peerMap){
            setLastActiveIndexer(peerMap.get(indexerUuid));

        }
        if(!indexing) {
            sendKey(keyPair.getPublic(), lastActiveIndexer);
            if (!saleItemList.isEmpty())
                sendAddSaleItemList(saleItemList, lastActiveIndexer);
            synchronized (peerMap){
                peerMap.forEach((key, value) -> {
                    if (!key.equals(uuid)) {
                        value.clearItems();
                    }
                });
            }
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

    //<editor-fold desc="Métodos para envio de eventos observáveis">
    /**
     * Envia um evento de mensagem recebida (unicast e multicast) à todos respectivos observadores
     * @param message mensagem do evento
     */
    private void onMessageEvent(String message) {
        messageEventListeners.forEach(listener->listener.onMessageReceived(message));
    }

    /**
     * Envia um evento de mensagem assincronamente (notificação dos observadores fazem parte de uma thread separada)
     * @param message mensagem do evento
     */
    private void onMessageEventAsync(String message) {
        Thread onMessageThread = new Thread(()->onMessageEvent(message));
        onMessageThread.setName("Asynchronous Message Event");
        onMessageThread.start();
    }

    /**
     * Envia um evento de alteração do valor de dinheiro à todos respectivos observadores
     * @param value novo valor de dinheiro
     */
    private void onMoneyEvent(float value){
        moneyEventListener.forEach(listener -> listener.onFloatEvent(value));
    }

    /**
     * Envia um evento de alteração do valor de dinheiro assincronamente
     * (notificação de observadores faz parte de uma thread separada)
     * @param value novo valor de dinheiro
     */
    private void onMoneyEventAsync(float value){
        Thread onMoneyEventThread = new Thread(() -> onMoneyEvent(value));
        onMoneyEventThread.setName("Money Event Thread");
        onMoneyEventThread.start();
    }

    /**
     * Envia um evento de transação de items à todos respectivos observadores
     * @param event evento de proposta/ transação de item
     */
    private void onItemProposalEvent(ItemProposalEvent event){
        itemProposalEventListeners.forEach(listener->listener.onItemProposalEventListener(event));
    }

    /**
     * Envia um evento de transação de items assincronamente
     * (notificação de observadores faz parte de uma thread separada)
     * @param event evento de proposta/ transação de item
     */
    private void onItemProposalEventAsync(ItemProposalEvent event){
        Thread onItemFoundEventThread = new Thread(()-> onItemProposalEvent(event));
        onItemFoundEventThread.setName("Item Found Event Thread");
        onItemFoundEventThread.start();
    }

    /**
     * Envia um evento de item adicionado, removido ou modificado à todos respectivos observadores
     * @param event evento de lista de item
     */
    private void onItemListEvent(ItemListEvent event){
        itemListEventListeners.forEach(listener->listener.onItemListEvent(event));
    }

    /**
     * Envia um evento de item adicionado, removido ou modificado assincronamente
     * (notificação de observadores faz parte de uma thread separada)
     * @param event evento de lista de item
     */
    private void onItemListEventAsync(ItemListEvent event){
        Thread onItemListEventThread = new Thread(()->onItemListEvent(event));
        onItemListEventThread.setName("Item List Event Thread");
        onItemListEventThread.start();
    }

    /**
     * Envia um evento indexador anunciado à todos respectivos observadores
     * @param connected indexador ativo?
     */
    private void onIndexerConnectionEvent(boolean connected){
        indexerConnectionEventListeners.forEach(listener->listener.onTriggerEvent(connected));
    }
    //</editor-fold>

    /**
     * Método de Callback para evento de novas conexões com servidor TCP.
     * Cria uma thread para aguardar uma requisição unicast e processá-la
     * @param connection nova conexão entre servidor e cliente
     */
    private void onTcpClientConnect(IUnicastSocketConnection connection){
        try {
            connection.setTimeout(TCP_TIMEOUT);
            Thread tcpConnection = new Thread(()->{
                try {
                    //Aguarda (sincronamente) conexão até tempo de timeout
                    String message = connection.getMessage();
                    //Processa mensagem recebida
                    processTcpMessage(message, new UnicastCommunicationContext(connection, null, null));
                } catch (IOException e) {
                    e.printStackTrace();
                }finally {
                    //Finaliza conexão ao final de requisição ou exceção
                    connection.disconnect();
                }
            });
            tcpConnection.setName("TCP Server Side Client Connection");
            tcpConnection.start();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    //<editor-fold desc="Métodos para requisições assíncronas Unicast">
    /**
     * Envia requisição de adição de item para venda por este processo
     * @param item item para venda
     * @param peer par para ser realizada requisição
     */
    private void sendAddSaleItem(SaleItem item, PeerOpponent peer){
        Thread sendSaleItemList = new Thread(()-> {
            TcpSynchroClient connection = null;
            //Realizar uma série de tentativas de reconexão se alguma falhar
            for(int i = 0; i < RECONNECTION_TRIES; i++) {
                try {
                    //Nova conexão com par em questão
                    connection = new TcpSynchroClient(peer.getIpAddress(), peer.getPortTcp());
                    connection.setTimeout(TCP_TIMEOUT);
                    //Introduz o ID deste processo ao servidor
                    tcpIntroductMessage(connection, null);
                    //Envia item para adicionar
                    tcpAddMessage(connection, null, item);
                    //Encerra requisição
                    tcpFinishMessage(connection, null);
                    //Espera um OK do servidor
                    String response = connection.getMessage();
                    if(processTcpMessage(response, new UnicastCommunicationContext(connection, peer.getUuid(), null)))
                        break;
                } catch (IOException e) {
                    e.printStackTrace();
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

    /**
     * Envia requisição de adição de uma lista de items para venda por este processo
     * @param itemList lista de items para venda
     * @param peer par para ser realizada requisição
     */
    private void sendAddSaleItemList(List<SaleItem> itemList, PeerOpponent peer){
        Thread sendSaleItemList = new Thread(()-> {
            TcpSynchroClient connection = null;
            //Realizar uma série de tentativas de reconexão se alguma falhar
            for(int i = 0; i < RECONNECTION_TRIES; i++) {
                try {
                    //Nova conexão com par em questão
                    connection = new TcpSynchroClient(peer.getIpAddress(), peer.getPortTcp());
                    connection.setTimeout(TCP_TIMEOUT);
                    //Introduz o ID deste processo ao servidor
                    tcpIntroductMessage(connection, null);
                    //Envia todos os items da lista
                    synchronized (itemList) {
                        for (SaleItem item : itemList)
                            tcpAddMessage(connection, null, item);
                    }
                    //Encerra requisição
                    tcpFinishMessage(connection, null);
                    String response = connection.getMessage();
                    //Espera OK do servidor
                    if(processTcpMessage(response, new UnicastCommunicationContext(connection, peer.getUuid(), null)))
                        break;
                } catch (IOException e) {
                    e.printStackTrace();
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

    /**
     * Envia requisição de compra de item para um par
     * @param item item para comprar
     * @param peer par para ser realizada requisição
     */
    private void sendBuyItem(SaleItem item, PeerOpponent peer){
        Thread sendBuyItem = new Thread(()-> {
            TcpSynchroClient connection = null;
            //Apenas uma tentativa será realizada, para evitar problemas de persistência
            try {
                //Nova conexão com par em questão
                connection = new TcpSynchroClient(peer.getIpAddress(), peer.getPortTcp());
                connection.setTimeout(TCP_TIMEOUT);
                //Introduz o ID este processo ao servidor com uma mensagem criptografada
                tcpIntroductMessage(connection, peer.getKey());
                //Realiza requisição de compra de item com mensagem criptografada
                tcpBuyMessage(connection, peer.getKey(), item);
                //Encerra requisição com mensagem criptografada
                tcpFinishMessage(connection, peer.getKey());
                //Espera um OK do servidor
                String response = connection.getMessage();
                if(processTcpMessage(response, new UnicastCommunicationContext(connection, peer.getUuid(), null))) {
                    //Se servidor confirmar, realize transação
                    setMoney(money - item.getPrice());
                    onItemProposalEventAsync(ItemProposalEvent.itemBought(item, peer));
                    peer.setReputation(peer.getReputation()+1);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (connection != null)
                    connection.disconnect();
                delay();
            }

        });
        sendBuyItem.setName("TCP Client Send Search Item by Description");
        sendBuyItem.start();
    }

    /**
     * Envia requisição de adição de chave publica deste processo
     * @param key chave publica deste processo
     * @param peer par para ser realizada requisição
     */
    private void sendKey(Key key, PeerOpponent peer){
        Thread sendPublicKey = new Thread(()->{
            TcpSynchroClient connection = null;
            //Realizar uma série de tentativas de reconexão se alguma falhar
            for(int i = 0; i < RECONNECTION_TRIES; i++) {
                try {
                    //Nova conexão com par em questão
                    connection = new TcpSynchroClient(peer.getIpAddress(), peer.getPortTcp());
                    connection.setTimeout(TCP_TIMEOUT);
                    //Transforma a chave em uma string de hexadecimais
                    String publicKey = keyToHex(key);
                    //Introduz o ID deste processo ao servidor
                    tcpIntroductMessage(connection, null);
                    //Envia chave publica deste processo
                    tcpKeyMessage(connection, null, publicKey);
                    //Encerra requisição
                    tcpFinishMessage(connection, null);
                    //Espera OK do servidor
                    String response = connection.getMessage();
                    if(processTcpMessage(response, new UnicastCommunicationContext(connection, null, null)))
                        break;
                } catch (IOException e) {
                    e.printStackTrace();
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

    /**
     * Envia requisição de remoção de item para venda por este processo
     * @param item item para ser removido
     * @param peer par para ser realizada requisição
     */
    private void sendRemoveSaleItem(SaleItem item, PeerOpponent peer){
        Thread sendSaleItemList = new Thread(()-> {
            TcpSynchroClient connection = null;
            //Realizar uma série de tentativas de reconexão se alguma falhar
            for(int i = 0; i < RECONNECTION_TRIES; i++) {
                try {
                    //Nova conexão com par em questão
                    connection = new TcpSynchroClient(peer.getIpAddress(), peer.getPortTcp());
                    connection.setTimeout(TCP_TIMEOUT);
                    //Introduz o ID deste processo ao servidor
                    tcpIntroductMessage(connection, null);
                    //Envia item para remoção
                    tcpRemoveMessage(connection, null, item);
                    //Encerra requisição
                    tcpFinishMessage(connection, null);
                    //Espera OK do servidor
                    String response = connection.getMessage();
                    if(processTcpMessage(response, new UnicastCommunicationContext(connection, peer.getUuid(), null)))
                        break;
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (connection != null)
                        connection.disconnect();
                    delay();
                }
            }
        });
        sendSaleItemList.setName("TCP Client Remove Sale Item");
        sendSaleItemList.start();
    }

    /**
     * Envia requisição de procura de item para compra por este processo
     * @param description descrição do item desejado
     * @param peer par para ser realizada requisição
     */
    private void sendSearchItemByDescription(String description, PeerOpponent peer){
        Thread sendSearchItemByDescriptionThread = new Thread(()-> {
            TcpSynchroClient connection = null;
            //Realizar uma série de tentativas de reconexão se alguma falhar
            for(int i = 0; i < RECONNECTION_TRIES; i++) {
                try {
                    //Nova conexão com par em questão
                    connection = new TcpSynchroClient(peer.getIpAddress(), peer.getPortTcp());
                    connection.setTimeout(TCP_TIMEOUT);
                    //Introduz o ID deste processo ao servidor
                    tcpIntroductMessage(connection, null);
                    //Envia descrição de item desejado
                    tcpSearchMessage(connection, null, description);
                    //Encerra requisição
                    tcpFinishMessage(connection, null);
                    //Espera OK do servidor
                    String response = connection.getMessage();
                    if(processTcpMessage(response, new UnicastCommunicationContext(connection, peer.getUuid(), null)))
                        break;
                } catch (IOException e) {
                    e.printStackTrace();
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
    //</editor-fold>

    /**
     * Processa menssagens multicast.
     * Mensagens processáveis:
     *  GREETING/#SENDER_ID/#SENDER_TCP_PORT
     *  INDEXING/#SENDER_ID/#INDEXER_TCP_PORT
     *  INVITE/#SENDER_ID/#DESTINY_ID/#SENDER_TCP_PORT
     *  LEAVING/#SENDER_ID
     * @param message mensagem para ser processada
     * @param address endereço IP de quem enviou mensagem
     */
    private void processMulticastMessage(String message, InetAddress address){
        //Envia evento de mensagem recebida
        onMessageEventAsync(String.format("Multicast [%05d]: %s", multicastPeer.getId(), message));
        //Particiona mensagem em tokens
        String msgTokens[] = message.split("/");
        //Recupera ID do remetente
        UUID senderUuid = UUID.fromString(msgTokens[1]);
        //Ignora mensagem quando remetente é o próprio objeto
        if(uuid.equals(senderUuid)) return;
        //Tipo de mensagem
        String messageType = msgTokens[0];
        switch (messageType){
            //GREETING/#SENDER_ID/#SENDER_TCP_PORT
            //<editor-fold desc="Processa 'GREETING'">
            //Quando mensagem for introdutória, adiciona remetente à lista de pares
            //e o responde para que ele faça o mesmo
            case "GREETING":
                //Responde remetente para ele poder adiconar este objeto à sua lista de pares
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
            //</editor-fold>

            //INDEXING/#SENDER_ID/#INDEXER_TCP_PORT
            //<editor-fold desc="Processa 'INDEXING'">
            //Quando mensagem é anúncio de disponibilidade do indexador, ativa flag de indexador anunciado
            //(#indexerAnounced), atualiza indexador se necessário e envia chave + items à venda caso este seja
            //um novo indexador
            case "INDEXING":
                //Indexador foi anunciado e ele não é este objeto
                setIndexerAnounced(true);
                setIndexing(false);
                synchronized (peerMap) {
                    //Adiciona remetente caso não o conheça
                    if(!peerMap.containsKey(senderUuid)) {
                        peerMap.put(senderUuid, new PeerOpponent()
                                .setUuid(senderUuid)
                                .setIpAddress(address)
                                .setPortTcp(Integer.parseInt(msgTokens[2])));
                    }
                }
                //Se for o primeiro indexador ativo, ou o indexador antigo foi substituido,
                //atualiza o indexador
                if(lastActiveIndexer == null || !senderUuid.equals(lastActiveIndexer.getUuid())) {
                    refreshIndexer(senderUuid);
                }
                break;
            //</editor-fold>

            //INVITE/#SENDER_ID/#DESTINY_ID/#SENDER_TCP_PORT
            //<editor-fold desc="Processa 'INVITE'">
            //Quando mensagem de convite, adiciona par à lista de pares disponíveis
            case "INVITE":
                //Destinatario
                UUID invited = UUID.fromString(msgTokens[2]);
                if(uuid.equals(invited)) {
                    //Se este objeto for o destinatário, adiciona o remetente à lista de pares
                    synchronized (peerMap) {
                        if(!peerMap.containsKey(senderUuid)) {
                            peerMap.put(senderUuid, new PeerOpponent()
                                    .setIpAddress(address)
                                    .setPortTcp(Integer.parseInt(msgTokens[3]))
                                    .setUuid(senderUuid));
                        }
                    }
                }
                break;
            //</editor-fold>

            //LEAVING/#SENDER_ID
            //<editor-fold desc="Processa 'LEAVING'">
            //Quando mensagem de retirada, retira para remetente da lista de pares ativos
            case "LEAVING":
                if(peerMap.containsKey(senderUuid)) {
                    synchronized (peerMap) {
                        peerMap.remove(senderUuid);
                    }
                }
                break;
            //</editor-fold>
            default:
        }
    }

    //<editor-fold desc="Métodos para envio de mensagens multicast">

    /**
     * Envia uma mensagem multicast de anúncio de disponibilidade do indexador
     */
    private void multicastIndexingMessage() {
        sendMulticastMessage(String.format("INDEXING/%s/%d",
                uuid.toString(), tcpPort));
    }

    /**
     * Envia uma mensagem multicast de convite à lista de pares do convidado
     * @param invited identifcado do convidado
     */
    private void multicastInviteMessage(UUID invited) {
        sendMulticastMessage(String.format("INVITE/%s/%s/%d",
                uuid.toString(), invited.toString(), tcpPort));
    }

    /**
     * Envia uma mensagem multicast de saudação à todos processos (com identificador e porta tcp)
     */
    private void multicastGreetingMessage() {
        sendMulticastMessage(String.format("GREETING/%s/%d",
                uuid.toString(), tcpPort));
    }

    /**
     * Envia uma mensagem multicast de que este processo esta desconectando
     */
    private void multicastLeavingMessage() {
        sendMulticastMessage(String.format("LEAVING/%s",
                uuid.toString()));
    }
    //</editor-fold>

    /**
     * Processa uma mensagem unicast. Funciona recursivamente, rechamando-se até que OK ou FINISH seja enviado.
     * Mensagens processáveis:
     *   ADD/#ITEM_DESC/#ITEM_PRICE
     *   BUY/#ITEM_DESC/#ITEM_PRICE
     *   ENCRYPTED/#ENCRYPTED_MESSAGE
     *   ERROR/#ERROR_CODE/#ERROR_MESSAGE
     *   FINISH
     *   FOUND/#ITEM_DESC/#ITEM_PRICE/#SELLER_ID/#SELLER_PUBLIC_KEY
     *   FOUND
     *   INTRODUCE/#SENDER_ID
     *   KEY/#PUBLIC_KEY
     *   OK
     *   REMOVE/#ITEM_DESC/#ITEM_PRICE
     *   SEARCH/#ITEM_DESC
     * @param message mensagem à ser processada
     * @param context armazena estado da requisição (cadeia de mensagens)
     * @return true caso receba um OK ou um FINISH (OK para cliente e FINISH para servidor)
     * @throws IOException excessões de timeout de leitura de mensagem síncrona
     */
    private boolean processTcpMessage(String message, UnicastCommunicationContext context)
            throws IOException {
        //Envia evento de mensagem à observadores
        onMessageEvent(String.format("Unicast   [%05d]: %s", context.getConnection().getId(), message));
        //Particiona mensagem em tokens
        String[] msgTokens = message.split("/");
        //Tipo de mensagem
        String messageType = msgTokens[0];
        switch (messageType){
            //ADD/#ITEM_DESC/#ITEM_PRICE
            //<editor-fold desc="Processa 'ADD'">
            //Adiciona novos item para venda para o par da parte oposta an conexão
            case "ADD":
                //Para adicionar um item, o par deve ter se anunciado antes
                if(context.getSenderUuid() != null){
                    //Esta mensagem é processada apenas pelo indexador
                    if (!indexing) {
                        tcpErrorMessage(context.getConnection(), context.getEncryptionKey(),
                                "Process is not indexer",
                                10);
                    }
                    //Cria novo item e o adiciona na lista de items vendidos pela parte oposta
                    SaleItem item = new SaleItem()
                            .setDescription(msgTokens[1])
                            .setPrice(Float.parseFloat(msgTokens[2]));
                    boolean failed = true;
                    synchronized (peerMap){
                        if (peerMap.containsKey(context.getSenderUuid())) {
                            peerMap.get(context.getSenderUuid()).addItem(item);
                            failed = false;
                        }
                    }
                    //Se indexador nao conhecer par, a requisição falha
                    if(failed)
                        tcpErrorMessage(context.getConnection(), context.getEncryptionKey(),
                                "Process don't know requester",
                                20);
                }
                else
                    tcpErrorMessage(context.getConnection(),null,
                            "Process have not announced itself", 30);
                break;
            //</editor-fold>

            //BUY/#ITEM_DESC/#ITEM_PRICE
            //<editor-fold desc="Processa 'BUY'">
            //Realiza a venda de um item para a parte oposta
            case "BUY":
                //Cria novo item e filtra lista de items vendidos para procura-lo
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
                        //Se o item for encontrado, remove ele da lista, armazena novo valor de
                        //dinheiro e envia requisição de remoção do item ao indexador
                        SaleItem item = optional.get();
                        synchronized (moneyLock) {
                            if(saleItemList.remove(item)) {
                                setMoney(money + wanted.getPrice());
                                if(!indexing)
                                    sendRemoveSaleItem(item, lastActiveIndexer);
                                else
                                    peerMap.get(uuid).removeItem(item);
                                //Por fim, lança evento aos observadores de items e transsação
                                onItemProposalEventAsync(ItemProposalEvent.itemSold(item, peerMap.get(context.getSenderUuid())));
                                onItemListEventAsync(new ItemListEvent(item, ItemListEvent.ItemListEventType.REMOVED));
                            }
                            else
                                tcpErrorMessage(context.getConnection(), context.getEncryptionKey(), "Transaction refused: internal problem", 60);
                        }
                    }
                    else
                        tcpErrorMessage(context.getConnection(), context.getEncryptionKey(), "Transaction refused: item not found", 60);
                }
                break;
            //</editor-fold>

            //ENCRYPTED/#ENCRYPTED_MESSAGE
            //<editor-fold desc="Processa 'ENCRYPTED'">
            //Decifra mensagem utilizando chave privada e reprocessa-a utilizando este método
            case "ENCRYPTED":
                try {
                    Cipher cipher = Cipher.getInstance(CRYPTO_ALGORITHM);
                    cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
                    String decrypted = new String(cipher.doFinal(DatatypeConverter.parseHexBinary(msgTokens[1])), StandardCharsets.UTF_8);
                    processTcpMessage(decrypted, context);
                } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
                    e.printStackTrace();
                }
                break;
            //</editor-fold>

            //ERROR/#ERROR_CODE/#ERROR_MESSAGE
            //<editor-fold desc="Processa 'ERROR'">
            //Lança uma exceção contendo a mensagem de erro e o código como parâmetro
            case "ERROR":
                String errorMsg = msgTokens[2];
                int errorCode = Integer.parseInt(msgTokens[1]);
                String e;
                if(context.getSenderUuid() != null)
                    e = String.format("ERROR %d: %s send \"%s\"", errorCode, context.getSenderUuid().toString(), errorMsg);
                else
                    e = String.format("ERROR %d: %s", errorCode, errorMsg);
                System.out.println(e);
                throw new IOException(e);
                //</editor-fold>

            //FINISH
            //<editor-fold desc="Processa 'FINISH'">
            //Envia OK ao remetente para confirmar finalização de requisição,
            //e retorna true para indicar execução bem sucedida
            case "FINISH":
                tcpOkMessage(context.getConnection(), context.getEncryptionKey());
                return true;
            //</editor-fold>

            //FOUND/#ITEM_DESC/#ITEM_PRICE/#SELLER_ID/#SELLER_PUBLIC_KEY
            //FOUND
            //<editor-fold desc="Processa 'FOUND'">
            //Armazena no contexto desta conexão os items encontrados após uma requisição de procura.
            //Ao receber o token sem parâmetros, inicializa requisição com proprietário do item
            //mais barato e de melhor reputação para compra
            case "FOUND":
                //Verifica se o parte oposta foi indentificada e ela é o indexador
                if(indexerUp && context.getSenderUuid() != null && context.getSenderUuid().equals(lastActiveIndexer.getUuid())) {
                    //Caso tenha sido enviado um item
                    if (msgTokens.length > 1) {
                        //Cria o item para compra
                        SaleItem saleItem = new SaleItem()
                                .setDescription(msgTokens[1])
                                .setPrice(Float.parseFloat(msgTokens[2]));
                        //Recupera ID do vendedor do item
                        UUID sellerUuid = UUID.fromString(msgTokens[3]);
                        //Recupera chave pública do vendedor
                        Key sellerKey = hexToPublicKey(msgTokens[4]);
                        //Se a lista do contexto não tiver sido inicializada, inicialize-a
                        if(context.getPeerItemPairList() == null)
                            context.setPeerItemPairList(new ArrayList<>());
                        boolean failed = true;
                        //Atualzia chave do item e adiciona ao contexto um par vendedor/item
                        synchronized (peerMap) {
                            if (peerMap.containsKey(sellerUuid)) {
                                failed = false;
                                PeerOpponent peer = peerMap.get(sellerUuid);
                                peer.setKey(sellerKey);
                                context.getPeerItemPairList().add(new Pair<>(peer, saleItem));
                            }
                        }
                        if(failed)
                            tcpErrorMessage(context.getConnection(),null, String.format("Identifier \"%s\" not known by peer", uuid.toString()),50);
                    }
                    //Caso tenha sido enviado uma indicação de fim de lista de items encontrados
                    //Verifica a lista esta vazia (ou seja, se o item pesquisado existe)
                    else if(context.getPeerItemPairList() != null && !context.getPeerItemPairList().isEmpty()){
                        //Se existir o item pesquisado, filtra a lista do contexto para encontrar o item mais barato
                        //do vendedor de maior reputação
                        Pair<PeerOpponent, SaleItem> pair = getPairPeerItemByPriceAndReputation(context.getPeerItemPairList());
                        //Lança envento de item encontrado para compra
                        //Obs.: Callback para requisição de compra é enviado junto com evento, para caso o usuário
                        //aceitar compra seja possível iniciar esta requisição.
                        if(pair != null)
                            onItemProposalEventAsync(ItemProposalEvent.itemFound(pair.getRight(),pair.getLeft(), this::sendBuyItem));
                    }
                    else
                        onItemProposalEventAsync(ItemProposalEvent.itemNotFound());
                }
                else
                    tcpErrorMessage(context.getConnection(),null, "Client have not requested search", 40);
                break;
            //</editor-fold>

            //INTRODUCE/#SENDER_ID
            //<editor-fold desc="Processa 'INTRODUCE'">
            //Atualiza contexto da comunicação para incluir ID da parte oposta
            case "INTRODUCE":
                context.setSenderUuid(UUID.fromString(msgTokens[1]));
                break;
            //</editor-fold>

            //KEY/#PUBLIC_KEY
            //<editor-fold desc="Processa 'KEY'">
            //Associa à parte oposta da conexão a chave enviada
            case "KEY":
                //Parte oposta deve ter se anunciado anteriormente
                if(context.getSenderUuid() != null) {
                    if (!indexing) {
                        tcpErrorMessage(context.getConnection(), context.getEncryptionKey(),
                                "Process is not indexer",
                                10);
                    }
                    //Converte chave de string
                    String hexString = msgTokens[1];
                    Key publicKey = hexToPublicKey(hexString);
                    boolean failed = true;
                    //Realiza associação entre chave e o par que a enviou
                    synchronized (peerMap){
                        if (peerMap.containsKey(context.getSenderUuid())) {
                            peerMap.get(context.getSenderUuid()).setKey(publicKey);
                            tcpOkMessage(context.getConnection(), context.getEncryptionKey());
                            failed = false;
                        }
                    }
                    if(failed)
                        tcpErrorMessage(context.getConnection(), context.getEncryptionKey(),
                                "Process don't know requester",
                                20);
                }
                else
                    tcpErrorMessage(context.getConnection(),null,
                            "Process have not announced itself", 30);
                break;
            //</editor-fold>

            //OK
            //<editor-fold desc="Processa 'OK'">
            //Resposta bem sucedida de uma mensagem de finalização de requisição
            case "OK":
                return true;
            //</editor-fold>

            //REMOVE/#ITEM_DESC/#ITEM_PRICE
            //<editor-fold desc="Processa 'REMOVE'">
            //Remove item para venda para o par da parte oposta na conexão
            case "REMOVE":
                //Para adicionar um item, o par deve ter se anunciado antes
                if(context.getSenderUuid() != null){
                    //Esta mensagem é processada apenas pelo indexador
                    if (!indexing) {
                        tcpErrorMessage(context.getConnection(), context.getEncryptionKey(),
                                "Process is not indexer",
                                10);
                    }
                    //Cria novo item e o remove da lista de items vendidos pela parte oposta
                    SaleItem item = new SaleItem()
                            .setDescription(msgTokens[1])
                            .setPrice(Float.parseFloat(msgTokens[2]));
                    boolean failed = true;
                    synchronized (peerMap){
                        if (peerMap.containsKey(context.getSenderUuid())) {
                            peerMap.get(context.getSenderUuid()).removeItem(item);
                            failed = false;
                        }
                    }
                    if(failed)
                        tcpErrorMessage(context.getConnection(), context.getEncryptionKey(),
                                "Process don't know requester",
                                20);
                }
                else
                    tcpErrorMessage(context.getConnection(),null,
                            "Process have not announced itself", 30);
                break;
            //</editor-fold>

            //SEARCH/#ITEM_DESC
            //<editor-fold desc="Processa 'SEARCH'">
            //Realiza uma busca por items que contenham descrição passada e responde remetente
            //com lista de items encontrados
            case "SEARCH":
                //Parte oposta deve ter se anunciado anteriormente
                if(context.getSenderUuid() != null) {
                    //Esta mensagem só pode ser processada pelo indexador
                    if (!indexing) {
                        tcpErrorMessage(context.getConnection(), context.getEncryptionKey(),
                                "Process is not indexer",
                                10);
                    }
                    //Recupera lista de items por descrição
                    List<Pair<PeerOpponent, SaleItem>> pairList = getPeerBySaleItemDescription(context.getSenderUuid(), msgTokens[1]);
                    if(pairList != null) {
                        //Para cada item da lista responde o remetente com o item passado
                        for (Pair<PeerOpponent, SaleItem> pair: pairList) {
                            PeerOpponent peer = pair.getLeft();
                            String key = keyToHex(peer.getKey());
                            SaleItem item = pair.getRight();
                            tcpFoundMessage(context.getConnection(), context.getEncryptionKey(), peer, item, key);
                        }
                    }
                    //Envia uma ultima mensagem para indicar fim da lista
                    tcpFoundMessage(context.getConnection(), context.getEncryptionKey());
                }
                else
                    tcpErrorMessage(context.getConnection(),null,
                            "Process have not announced itself", 30);
                break;
            //</editor-fold>
            default:
        }
        //Rechama este método até que OK ou FINISH recebidos
        return processTcpMessage(context.getConnection().getMessage(), context);
    }

    //<editor-fold desc="Métodos para envio de mensagens unicast">

    /**
     * Envia à parte oposta da conexão mensagem para adicionar item à lista de items à venda
     * @param connection conexão para enviar mensagem
     * @param key chave para encriptar mensagem (null para não realizar criptografia)
     * @param item item que se deseja adicionar
     * @throws IOException caso conexão tenha sido interrompida
     */
    private void tcpAddMessage(IUnicastSocketConnection connection,
                               Key key,
                               SaleItem item)
            throws IOException {
        String message = String.format("ADD/%s/%.2f", item.getDescription(), item.getPrice());
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    /**
     * Envia à parte oposta da conexão mensagem para compra de um item
     * @param connection conexão para enviar mensagem
     * @param key chave para encriptar mensagem (null para não realizar criptografia)
     * @param item item desejado
     * @throws IOException caso a conexão tenha sido interrompida
     */
    private void tcpBuyMessage(IUnicastSocketConnection connection,
                               Key key,
                               SaleItem item)
            throws IOException {
        String message = String.format("BUY/%s/%.02f", item.getDescription(), item.getPrice());
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    /**
     * Envia à parte oposta da conexão mensagem criptografada
     * @param connection conexão para enviar mensagem
     * @param key chave para encriptar mensagem (deve ser diferente de null)
     * @param message mensagem que se deseja criptografar
     * @throws IOException caso a conexão tenha sido interrompida
     */
    private void tcpEncryptedMessage(IUnicastSocketConnection connection,
                                     Key key,
                                     String message)
            throws IOException{
        if(key != null) {
            try {
                Cipher cipher = Cipher.getInstance(CRYPTO_ALGORITHM);
                cipher.init(Cipher.ENCRYPT_MODE, key);
                String encrypted = DatatypeConverter.printHexBinary(cipher.doFinal(message.getBytes(StandardCharsets.UTF_8)));
                connection.sendMessage(String.format("ENCRYPTED/%s",encrypted));
            } catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | InvalidKeyException | NoSuchPaddingException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Envia à parte oposta da conexão mensagem de erro
     * @param connection conexão para enviar mensagem
     * @param key chave para encriptar mensagem (null para não realizar criptografia)
     * @param errorMessage mensagem de erro
     * @param errorCode código do erro
     * @throws IOException caso a conexão tenha sido interrompida
     */
    private void tcpErrorMessage(IUnicastSocketConnection connection,
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

    /**
     * Envia à parte oposta da conexão mensagem de finalização de requisição
     * @param connection conexão para enviar mensagem
     * @param key chave para encriptar mensagem (null para não realizar criptografia)
     * @throws IOException caso conexão tenha sido interrompida
     */
    private void tcpFinishMessage(IUnicastSocketConnection connection,
                                  Key key)
            throws IOException {
        String message = String.format("FINISH");
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    /**
     * Envia à parte oposta da conexão mensagem de items encontrados após requisição de pesquisa
     * @param connection conexão para enviar mensagem
     * @param key chave para encriptar mensagem (null para não realizar criptografia)
     * @param peer vendedor do item solicitado
     * @param item item solicitado
     * @param peerKey chave pública do vendedor
     * @throws IOException caso conexão tenha sido interrompida
     */
    private void tcpFoundMessage(IUnicastSocketConnection connection,
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

    /**
     * Envia à parte oposta da conexão mensagem de finalização de lista de items encontrados
     * @param connection conexão para enviar mensagem
     * @param key chave para encriptar mensagem (null para não realizar criptografia)
     * @throws IOException caso a conexão tenha sido interrompida
     */
    private void tcpFoundMessage(IUnicastSocketConnection connection,
                                 Key key)
            throws IOException {
        String message = "FOUND";
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    /**
     * Envia à parte oposta da conexão mensagem contendo id do remetente
     * @param connection conexão para enviar mensagem
     * @param key chave para encriptar mensagem (null para não realizar criptografia)
     * @throws IOException caso a conexão tenha sido interrompida
     */
    private void tcpIntroductMessage(IUnicastSocketConnection connection,
                                     Key key)
            throws IOException {
        String message = String.format("INTRODUCE/%s", uuid.toString());
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    /**
     * Envia à parte oposta da conexão mensagem contendo chave
     * @param connection conexão para enviar mensagem
     * @param key chave para encriptar mensagem (null para não realizar criptografia)
     * @param encryptionKey chave anunciada
     * @throws IOException caso conexão tenha sido interrompida
     */
    private void tcpKeyMessage(IUnicastSocketConnection connection,
                               Key key,
                               String encryptionKey)
            throws IOException {
        String message = String.format("KEY/%s", encryptionKey);
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    /**
     * Envia à parte oposta da conexão mensagem de OK, indicando requisição finalizada com sucesso
     * @param connection conexão para enviar mensagem
     * @param key chave para encriptar mensagem (null para não realizar criptografia)
     * @throws IOException caso conexão tenha sido interrompida
     */
    private void tcpOkMessage(IUnicastSocketConnection connection,
                              Key key)
            throws IOException {
        String message = String.format("OK");
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    /**
     * Envia à parte oposta da conexão mensagem de remoção do item solicitado
     * @param connection conexão para enviar mensagem
     * @param key chave para encriptar mensagem (null para não realizar criptografia)
     * @param item item para remoção
     * @throws IOException caso conexão tenha sido interrompida
     */
    private void tcpRemoveMessage(IUnicastSocketConnection connection,
                               Key key,
                               SaleItem item)
            throws IOException {
        String message = String.format("REMOVE/%s/%.2f", item.getDescription(), item.getPrice());
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }

    /**
     * Envia à parte oposta da conexão mensagem de pesquisa por item com descrição solicitada
     * @param connection conexão para enviar mensagem
     * @param key chave para encriptar mensagem (null para não realizar criptografia)
     * @param description descrição do item desejado
     * @throws IOException caso a conexão tenha sido interrompida
     */
    private void tcpSearchMessage(IUnicastSocketConnection connection,
                                  Key key,
                                  String description)
            throws IOException {
        String message = String.format("SEARCH/%s", description);
        if(key != null)
            tcpEncryptedMessage(connection, key, message);
        else
            connection.sendMessage(message);
    }
    //</editor-fold>

    /**
     * Retorna uma lista de items que contenham a descrição solicitada associado com seus respectivos vendedores
     * @param requester id do solicitante (será ignorado na pesquisa)
     * @param saleItemDescription descrição do item desejado
     * @return uma lista contendo items que batem com a descrição associado aos seus respectivos vendedores
     */
    private List<Pair<PeerOpponent, SaleItem>> getPeerBySaleItemDescription(UUID requester, String saleItemDescription){
        synchronized (peerMap) {
            if(!peerMap.containsKey(requester))
                return null;

            return peerMap
                    .entrySet()
                    .parallelStream()
                    .filter( pair -> !requester.equals(pair.getKey()) ) //filtra items que sejam vendidos pelo solicitante
                    .flatMap(
                            pair ->
                            pair.getValue().getItems(saleItemDescription) //filtra por descrição de item
                            .parallelStream()
                            .map( item -> new Pair<>(pair.getValue(), item)) //associa vendedor ao item vendido
                    )
                    .collect( Collectors.toList() );
        }
    }

    /**
     * Filtra lista de items/ vendores por item de menor preço (vendido pelo vendedor com melhor reputação)
     * @param pairList lista para ser filtrada, contém pares item/ vendedor do item
     * @return par item/ vendedor contendo item de menor preço vendido pelo (vendedor com melhor reputação)
     */
    private Pair<PeerOpponent, SaleItem> getPairPeerItemByPriceAndReputation(List<Pair<PeerOpponent, SaleItem>> pairList) {
        return pairList
               .parallelStream()
               .min((o1, o2) -> Float.valueOf(o1.getRight().getPrice()).equals(o2.getRight().getPrice()) ? //Caso preços sejam iguais ...
                       Integer.compare(o2.getLeft().getReputation(), o1.getLeft().getReputation()): //... realiza comparação de reputação ...
                       Float.compare(o1.getRight().getPrice(), o2.getRight().getPrice()) //... caso contrário realiza comparação de preços
               )
               .orElse(null);
    }

    /**
     * Transforma uma chave em string (converte bytes da chave em strings hexadecimais)
     * @param key chave para conversão
     * @return string representando chave passada
     */
    private String keyToHex(Key key) {
        return DatatypeConverter.printHexBinary(key.getEncoded());
    }

    /**
     * Converte uma string de bytes impressa em hexadecimal para uma chave pública
     * @param hex string de bytes em hexadecimal
     * @return chave pública correspondente
     */
    private Key hexToPublicKey(String hex){
        try {
            return KeyFactory.getInstance(CRYPTO_ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(DatatypeConverter.parseHexBinary(hex)));
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(String.format("Expected valid string of printed bytes for key generation (found: %s)",hex), e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Expected "+ CRYPTO_ALGORITHM +" algorithm for encryption", e);
        }
    }

    /**
     * Modifica valor de flag (sincrono) e envia evento de estado do indexador
     * @param indexerUp novo valor de flag
     */
    private void setIndexerUp(boolean indexerUp){
        if(this.indexerUp != indexerUp)
            onIndexerConnectionEvent(indexerUp);
        synchronized (indexerUpLock) {
            this.indexerUp = indexerUp;
        }
    }

    /**
     * Método sincrono para modificar {@link #indexing}
     * @param indexing novo valor de flag
     */
    private synchronized void setIndexing(boolean indexing){
        this.indexing = indexing;
    }

    /**
     * Método sincrono para modificar {@link #indexerAnounced}
     * @param indexerAnounced novo valor de flag
     */
    private synchronized void setIndexerAnounced(boolean indexerAnounced){
        this.indexerAnounced = indexerAnounced;
    }

    /**
     * Método sincrono para modificar {@link #lastActiveIndexer}
     * @param lastActiveIndexer novo valor de referência ao indexador
     */
    private synchronized void setLastActiveIndexer(PeerOpponent lastActiveIndexer) {
        this.lastActiveIndexer = lastActiveIndexer;
    }

    /**
     * Thread dorme por n*{@link #DELTA} milisegundos
     * @param n quantidade de {@link #DELTA}'s para esperar
     */
    private static void delay(int n){
        try {
            Thread.sleep(n*DELTA);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Thread dorme por {@link #DELTA} milisegundos
     */
    private static void delay(){
        delay(1);
    }
}