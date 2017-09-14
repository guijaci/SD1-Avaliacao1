package edu.utfpr.guilhermej.sisdist.av1.network;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.function.Consumer;

public class TcpSynchroServerSideClient implements IUnicastSocketConnection {
    private TcpServer parent;
    private Socket clientSocket;
    private Consumer<IUnicastSocketConnection> unregisterFromParent;
    private DataOutputStream out;
    private DataInputStream in;

    private boolean executionEnable = false;

    public TcpSynchroServerSideClient(TcpServer parent, Socket clientSocket, Consumer<IUnicastSocketConnection> unregisterFromParent){
        this.parent = parent;
        this.clientSocket = clientSocket;
        this.unregisterFromParent = unregisterFromParent;

        executionEnable = true;

        try {
            out = new DataOutputStream(clientSocket.getOutputStream());
            in = new DataInputStream(clientSocket.getInputStream());
        } catch (IOException e) {
            System.out.println("TCP Conection IO: "+e.getMessage());
        }
    }

    public TcpServer getParent() {
    return parent;
    }


    /**
     * Recupera última mensagem enviada, ou bloqueia caso nenhuma haver chego.
     * Desbloqueia após timeout, se configurado
     * @return mensagem recuperada
     * @throws IOException caso conexão esteja indisponível
     */
    @Override
    public String getMessage() throws IOException {
        if(!isConnected())
            throw new IOException("TCP Connection closed.");
        String message = in.readUTF();
        System.out.println(String.format("Unicast   [%05d]: %s", getId(), message));
        return message;
    }

    /**
     * Envia mensagem a parte oposta (sincrono)
     * @param message mensagem a ser enviada
     * @throws IOException caso conexão não esteja disponível
     */
    @Override
    public void sendMessage(String message) throws IOException{
        if(!isConnected())
            throw new IOException("TCP Connection closed.");
        out.writeUTF(message);
    }

    /**
     * Configura tempo de timeout de conexões sincronas
     * @param timeout tempo de timeout em milisegundos
     * @throws SocketException caso conexão não esteja disponível
     */
    @Override
    public void setTimeout(int timeout) throws SocketException {
        clientSocket.setSoTimeout(timeout);
    }

    /**
     * Retorna de soquete esta disponível para conexão
     * @return estado da conexão
     */
    @Override
    public boolean isConnected() {
        return executionEnable && clientSocket.isConnected() && !clientSocket.isClosed() && clientSocket.isBound();
    }

    /**
     * Retorna identificador da conexão
     * @return identificador (em geral valor da porta do lado do cliente)
     */
    @Override
    public int getId() {
        return clientSocket.getPort();
    }

    /**
     * Realiza desconexão e finalizações necessários à conexão
     */
    @Override
    public void disconnect() {
        unregisterFromParent.accept(this);
        executionEnable = false;
        try {
            if(in != null)
                in.close();
            if(out != null)
                out.close();
            if (clientSocket != null && !clientSocket.isClosed())
                clientSocket.close();
        } catch (IOException e) {
            System.out.println("Server Side Client Connection IO: " + e.getMessage());
        }
    }
}
