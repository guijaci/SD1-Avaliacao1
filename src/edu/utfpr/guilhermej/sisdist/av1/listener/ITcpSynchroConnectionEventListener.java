package edu.utfpr.guilhermej.sisdist.av1.listener;

import edu.utfpr.guilhermej.sisdist.av1.network.IUnicastSocketConnection;

public interface ITcpSynchroConnectionEventListener {
    void onTcpSecureConectionEventListener(IUnicastSocketConnection connection);
}
