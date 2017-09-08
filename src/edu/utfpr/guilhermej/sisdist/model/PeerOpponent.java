package edu.utfpr.guilhermej.sisdist.model;

import java.net.InetAddress;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;

public class PeerOpponent implements Comparable<PeerOpponent>{
    private UUID uuid;
    private InetAddress ipAddress;
    private int portTcp;
    private PublicKey key;
    private boolean hasKey;

    private ArrayList<SaleItem> saleItems;

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

    public PublicKey getKey() {
        return key;
    }

    public PeerOpponent setKey(PublicKey key) {
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
        return saleItems.get(index);
    }

    public int countItems(){
        return saleItems.size();
    }

    public PeerOpponent addItem(SaleItem item){
        saleItems.add(item);
        return this;
    }

    public boolean removeItem(SaleItem item){
        return saleItems.remove(item);
    }

    public PeerOpponent foreachItem(Consumer<? super SaleItem> action) {
        saleItems.forEach(action);
        return this;
    }

    @Override
    public int compareTo(PeerOpponent o) {
        return uuid.compareTo(o.uuid);
    }

}
