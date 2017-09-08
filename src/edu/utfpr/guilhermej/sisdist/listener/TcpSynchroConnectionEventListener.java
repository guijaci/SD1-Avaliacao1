package edu.utfpr.guilhermej.sisdist.listener;

import edu.utfpr.guilhermej.sisdist.network.TcpSynchroServerSideClient;

public interface TcpSynchroConnectionEventListener {
    void onTcpSecureConectionEventListener(TcpSynchroServerSideClient connection);
}
