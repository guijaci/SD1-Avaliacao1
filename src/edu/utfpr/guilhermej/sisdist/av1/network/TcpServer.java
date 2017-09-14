package edu.utfpr.guilhermej.sisdist.av1.network;

import edu.utfpr.guilhermej.sisdist.av1.listener.ITcpSynchroConnectionEventListener;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Classe para inicializar um servidor TCP
 */
public class TcpServer {
    /** Timeout para inicializar uma conexão */
    private final int TIMEOUT = 3000;

    /** Socket do servidor */
    ServerSocket listenSocket;

    /** Lista de observadores de novas conexões */
    private ArrayList<ITcpSynchroConnectionEventListener> connectionListeners;
    /** Lista de conexões ativas com clientes */
    private ArrayList<TcpSynchroServerSideClient> clientConnections;
    /** porta em que servidor permanecerá escutando */
    private int port;
    /** flag para indicar finalização de threads */
    private boolean executionEnable = false;

    /**
     * Construtor padrão de servidor TCP
     * @param port porta para servidor permanecer escutando
     * @throws IOException caso não seja possível estabelecer um servidor
     */
    public TcpServer(int port) throws IOException {
        this.port = port;
        listenSocket = new ServerSocket(port);
        clientConnections = new ArrayList<>();
        connectionListeners = new ArrayList<>();
        executionEnable = true;

        initTcpServerThread();
    }

    /**
     * Disconecta servidor e todas conexões com clientes ainda ativas
     */
    public void disconnect(){
        executionEnable = false;
        clientConnections.forEach(TcpSynchroServerSideClient::disconnect);
        try{
            if(listenSocket != null && !listenSocket.isClosed())
                listenSocket.close();
        } catch (IOException e) {
                e.printStackTrace();
        }
    }

    /**
     * Inscreve observador de novas conexões com servidor
     * @param connectionListener observador de novas conexões para ser inscrito
     */
    public void addTcpConnectionListener(ITcpSynchroConnectionEventListener connectionListener){
        connectionListeners.add(connectionListener);
    }

    /**
     * Cancela inscrição de observador de novas conexões com servidor
     * @param connectionListener observador de novas conexões para cancelar inscrição
     */
    public void removeTcpConnectionListener(ITcpSynchroConnectionEventListener connectionListener){
        connectionListeners.remove(connectionListener);
    }

    /**
     * Inicia thread do servidor tcp
     */
    private void initTcpServerThread() {
        Thread tcpServerThread = new Thread(()->{
            try {
                Socket clientSocket;
                while (executionEnable) {
                    try {
                        //Aguarda nova conexão até estourar timeout
                        clientSocket = listenSocket.accept();
                    } catch (IOException e) {
                        //Reinicia loop após um timeout
                        Thread.yield();
                        continue;
                    }
                    //Se houver uma conexão, cria nova conexão do lado do servidor
                    TcpSynchroServerSideClient connection = new TcpSynchroServerSideClient(this, clientSocket, clientConnections::remove);
                    //Lança novo evento de conexão para observadores
                    tcpClientConnectionEvent(connection);
                }
                if(listenSocket != null && !listenSocket.isClosed())
                    listenSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        tcpServerThread.setName("TCP Server");
        tcpServerThread.start();
    }

    /**
     * Lança evento de nova conexão realizada
     * @param connection conexão realizada
     */
    private void tcpClientConnectionEvent(TcpSynchroServerSideClient connection){
        connectionListeners.forEach(listener -> listener.onTcpSecureConectionEventListener(connection));
    }
}
