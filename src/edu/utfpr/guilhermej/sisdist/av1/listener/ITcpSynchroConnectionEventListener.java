package edu.utfpr.guilhermej.sisdist.av1.listener;

import edu.utfpr.guilhermej.sisdist.av1.network.TcpSynchroServerSideClient;

public interface ITcpSynchroConnectionEventListener {
    void onTcpSecureConectionEventListener(TcpSynchroServerSideClient connection);
}
