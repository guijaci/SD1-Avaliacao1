package edu.utfpr.guilhermej.sisdist.model;

import java.util.UUID;

public class PeerOpponent implements Comparable<PeerOpponent>{
    private UUID uuid;
    private int portTcp;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public int getPortTcp() {
        return portTcp;
    }

    public void setPortTcp(int portTcp) {
        this.portTcp = portTcp;
    }

    @Override
    public int compareTo(PeerOpponent o) {
        return uuid.compareTo(o.uuid);
    }
}
