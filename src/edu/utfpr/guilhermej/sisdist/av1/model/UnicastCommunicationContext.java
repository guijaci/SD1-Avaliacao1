package edu.utfpr.guilhermej.sisdist.av1.model;

import edu.utfpr.guilhermej.sisdist.av1.network.IUnicastSocketConnection;
import edu.utfpr.guilhermej.sisdist.av1.util.Pair;

import java.security.Key;
import java.util.List;
import java.util.UUID;

/**
 * Classe armazena contexto de comunicação unicast entre dois processos
 */
class UnicastCommunicationContext {
    /** Conexão do contexto */
    private IUnicastSocketConnection connection;
    /** ID da parte oposta da conexão */
    private UUID senderUuid;
    /** Chave para realizar criptografia de mensagens */
    private Key encryptionKey;
    /** Lista de items/ vendedores resultado de uma pesquisa */
    private List<Pair<PeerOpponent, SaleItem>> peerItemPairList;

    UnicastCommunicationContext(IUnicastSocketConnection connection, UUID senderUuid, Key encryptionKey) {
        this.connection = connection;
        this.senderUuid = senderUuid;
        this.encryptionKey = encryptionKey;
        peerItemPairList = null;
    }

    public IUnicastSocketConnection getConnection() {
        return connection;
    }

    public UnicastCommunicationContext setConnection(IUnicastSocketConnection connection) {
        this.connection = connection;
        return this;
    }

    public UUID getSenderUuid() {
        return senderUuid;
    }

    public UnicastCommunicationContext setSenderUuid(UUID senderUuid) {
        this.senderUuid = senderUuid;
        return this;
    }

    public Key getEncryptionKey() {
        return encryptionKey;
    }

    public UnicastCommunicationContext setEncryptionKey(Key encryptionKey) {
        this.encryptionKey = encryptionKey;
        return this;
    }

    public List<Pair<PeerOpponent, SaleItem>> getPeerItemPairList() {
        return peerItemPairList;
    }

    public UnicastCommunicationContext setPeerItemPairList(List<Pair<PeerOpponent, SaleItem>> peerItemPairList) {
        this.peerItemPairList = peerItemPairList;
        return this;
    }

    /** Retorna ID da conexão, se houver uma*/
    public int getConnectId(){
        return connection != null ? connection.getId() : -1;
    }
}
