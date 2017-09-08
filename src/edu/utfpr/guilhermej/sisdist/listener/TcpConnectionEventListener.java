package edu.utfpr.guilhermej.sisdist.listener;

import edu.utfpr.guilhermej.sisdist.network.TcpServerSideClient;

public interface TcpConnectionEventListener {
    void onTcpConection(TcpServerSideClient conection);
}
