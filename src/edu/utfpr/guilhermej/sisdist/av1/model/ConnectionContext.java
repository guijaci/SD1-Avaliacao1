package edu.utfpr.guilhermej.sisdist.av1.model;

import edu.utfpr.guilhermej.sisdist.av1.network.ISocketConnection;

import java.security.Key;
import java.util.List;
import java.util.UUID;

class ConnectionContext {
    private ISocketConnection connection;
    private UUID senderUuid;
    private Key encryptionKey;
    private List<SaleItem> itemList;

    ConnectionContext(ISocketConnection connection, UUID senderUuid, Key encryptionKey) {
        this.connection = connection;
        this.senderUuid = senderUuid;
        this.encryptionKey = encryptionKey;
        itemList = null;
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

    public List<SaleItem> getItemList() {
        return itemList;
    }

    public ConnectionContext setItemList(List<SaleItem> itemList) {
        this.itemList = itemList;
        return this;
    }
}
