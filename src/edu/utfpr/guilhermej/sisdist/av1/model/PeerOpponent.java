package edu.utfpr.guilhermej.sisdist.av1.model;

import java.net.InetAddress;
import java.security.Key;
import java.security.PublicKey;
import java.util.*;
import java.util.function.Consumer;

public class PeerOpponent implements Comparable<PeerOpponent>{
    private UUID uuid;
    private InetAddress ipAddress;
    private int portTcp;
    private Key key;
    private boolean hasKey;

    private List<SaleItem> saleItemList;

    public UUID getUuid() {
        return uuid;
    }

    public PeerOpponent setUuid(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    public int getPortTcp() {
        return portTcp;
    }

    public PeerOpponent setPortTcp(int portTcp) {
        this.portTcp = portTcp;
        return this;
    }

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    public PeerOpponent setIpAddress(InetAddress ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public Key getKey() {
        return key;
    }

    public PeerOpponent setKey(Key key) {
        this.key = key;
        return this;
    }

    public boolean isHasKey() {
        return hasKey;
    }

    public PeerOpponent setHasKey(boolean hasKey) {
        this.hasKey = hasKey;
        return this;
    }

    public SaleItem getItem (int index){
        return saleItemList.get(index);
    }

    public int countItems(){
        return saleItemList.size();
    }

    public PeerOpponent addItem(SaleItem item){
        saleItemList.add(item);
        return this;
    }

    public boolean removeItem(SaleItem item){
        return saleItemList.remove(item);
    }

    public PeerOpponent foreachItem(Consumer<? super SaleItem> action) {
        saleItemList.forEach(action);
        return this;
    }

    @Override
    public int compareTo(PeerOpponent o) {
        return uuid.compareTo(o.uuid);
    }

}
