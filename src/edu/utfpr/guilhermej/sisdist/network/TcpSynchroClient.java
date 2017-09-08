package edu.utfpr.guilhermej.sisdist.network;

import java.net.Socket;

public class TcpSynchroClient {
    private int tcpPort;
    private Socket clientSide;

    public TcpSynchroClient(int tcpPort){
        this.tcpPort = tcpPort;
    }
}
