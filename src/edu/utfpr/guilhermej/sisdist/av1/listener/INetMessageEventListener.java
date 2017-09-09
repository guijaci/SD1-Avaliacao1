package edu.utfpr.guilhermej.sisdist.av1.listener;

import java.net.InetAddress;

public interface INetMessageEventListener {
    void onNetMessageReceived(String message, InetAddress address);
}
