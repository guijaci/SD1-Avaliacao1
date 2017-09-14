package edu.utfpr.guilhermej.sisdist.av1.network;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

public class TcpSynchroClient implements IUnicastSocketConnection {
    private Socket clientSide;

    private final DataOutputStream out;
    private final DataInputStream in;

    public TcpSynchroClient(InetAddress serverAddress, int serverPort) throws IOException {
        clientSide = new Socket(serverAddress, serverPort);

        out = new DataOutputStream(clientSide.getOutputStream());
        in = new DataInputStream(clientSide.getInputStream());
    }

    /**
     * Configura tempo de timeout de conexões sincronas
     * @param timeout tempo de timeout em milisegundos
     * @throws SocketException caso conexão não esteja disponível
     */
    @Override
    public void setTimeout(int timeout) throws SocketException {
        clientSide.setSoTimeout(timeout);
    }

    /**
     * Envia mensagem a parte oposta (sincrono)
     * @param message mensagem a ser enviada
     * @throws IOException caso conexão não esteja disponível
     */
    @Override
    public void sendMessage(String message) throws IOException {
        if(!isConnected())
            throw new IOException("TCP Connection closed.");
        out.writeUTF(message);
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
     * Retorna de soquete esta disponível para conexão
     * @return estado da conexão
     */
    @Override
    public boolean isConnected(){
        return clientSide.isConnected() && !clientSide.isClosed() && clientSide.isConnected() && clientSide.isBound();
    }

    /**
     * Retorna identificador da conexão
     * @return identificador (em geral valor da porta do lado do cliente)
     */
    @Override
    public int getId() {
        return clientSide.getLocalPort();
    }

    /**
     * Realiza desconexão e finalizações necessários à conexão
     */
    @Override
    public void disconnect() {
        try {
            if(in != null)
                in.close();
            if(out != null)
                out.close();
            if (clientSide != null && !clientSide.isClosed())
                clientSide.close();
        } catch (IOException e) {
            System.out.println("Client Connection IO: " + e.getMessage());
        }
    }
}
