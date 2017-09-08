package edu.utfpr.guilhermej.sisdist.listener;

import java.net.InetAddress;

public interface NetMessageEventListener {
    void onNetMessageReceived(String message, InetAddress address);
}
