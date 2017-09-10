package edu.utfpr.guilhermej.sisdist.av1.model;

import java.net.InetAddress;
import java.security.Key;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PeerOpponent implements Comparable<PeerOpponent>{
    private UUID uuid;
    private InetAddress ipAddress;
    private Key key;
    private int portTcp;
    private int reputation;

    private List<SaleItem> saleItemList;

    public PeerOpponent(){
        saleItemList = new ArrayList<>();
        reputation = 0;
    }

    public UUID getUuid() {
        return uuid;
    }

    public PeerOpponent setUuid(UUID uuid) {
        this.uuid = uuid;
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

    public int getPortTcp() {
        return portTcp;
    }

    public PeerOpponent setPortTcp(int portTcp) {
        this.portTcp = portTcp;
        return this;
    }

    public int getReputation() {
        return reputation;
    }

    public PeerOpponent setReputation(int reputation) {
        this.reputation = reputation;
        return this;
    }

    public SaleItem getItem (int index){
        return saleItemList.get(index);
    }

    public List<SaleItem> getItems (String description){
        return saleItemList
                .stream()
                .filter( i -> i.getDescription().equals(description) )
                .collect( Collectors.toList() );
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

    public PeerOpponent sortItems(Comparator<SaleItem> comparision){
        saleItemList.sort(comparision);
        return this;
    }

    public void clearItems(){
        saleItemList.clear();
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
