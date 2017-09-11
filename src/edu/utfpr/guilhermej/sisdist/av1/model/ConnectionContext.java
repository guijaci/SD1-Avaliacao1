package edu.utfpr.guilhermej.sisdist.av1.model;

import edu.utfpr.guilhermej.sisdist.av1.network.ISocketConnection;
import edu.utfpr.guilhermej.sisdist.av1.util.Pair;

import java.security.Key;
import java.util.List;
import java.util.UUID;

class ConnectionContext {
    private ISocketConnection connection;
    private UUID senderUuid;
    private Key encryptionKey;
    private List<Pair<PeerOpponent, SaleItem>> peerItemPairList;

    ConnectionContext(ISocketConnection connection, UUID senderUuid, Key encryptionKey) {
        this.connection = connection;
        this.senderUuid = senderUuid;
        this.encryptionKey = encryptionKey;
        peerItemPairList = null;
    }

    public ISocketConnection getConnection() {
        return connection;
    }

    public ConnectionContext setConnection(ISocketConnection connection) {
        this.connection = connection;
        return this;
    }

    public UUID getSenderUuid() {
        return senderUuid;
    }

    public ConnectionContext setSenderUuid(UUID senderUuid) {
        this.senderUuid = senderUuid;
        return this;
    }

    public Key getEncryptionKey() {
        return encryptionKey;
    }

    public ConnectionContext setEncryptionKey(Key encryptionKey) {
        this.encryptionKey = encryptionKey;
        return this;
    }

    public List<Pair<PeerOpponent, SaleItem>> getPeerItemPairList() {
        return peerItemPairList;
    }

    public ConnectionContext setPeerItemPairList(List<Pair<PeerOpponent, SaleItem>> peerItemPairList) {
        this.peerItemPairList = peerItemPairList;
        return this;
    }

    public int getConnectId(){
        return connection != null ? connection.getId() : -1;
    }
}
