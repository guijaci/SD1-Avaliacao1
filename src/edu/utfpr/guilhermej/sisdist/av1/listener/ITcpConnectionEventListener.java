package edu.utfpr.guilhermej.sisdist.av1.listener;

import edu.utfpr.guilhermej.sisdist.av1.network.TcpServerSideClient;

public interface ITcpConnectionEventListener {
    void onTcpConection(TcpServerSideClient conection);
}
