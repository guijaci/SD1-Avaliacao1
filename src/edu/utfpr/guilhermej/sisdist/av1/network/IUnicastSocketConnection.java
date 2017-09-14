package edu.utfpr.guilhermej.sisdist.av1.network;

import java.io.IOException;
import java.net.SocketException;

/**
 * Interface para conexões unicast, tanto do lado do servidor quanto do lado do cliente
 * Permite que {@link edu.utfpr.guilhermej.sisdist.av1.model.Peer} processe menssagens
 * do lado do servidor e do cliente da mesma forma
 */
public interface IUnicastSocketConnection {
    /**
     * Configura tempo de timeout de conexões sincronas
     * @param timeout tempo de timeout em milisegundos
     * @throws SocketException caso conexão não esteja disponível
     */
    void setTimeout(int timeout) throws SocketException;

    /**
     * Envia mensagem a parte oposta (sincrono)
     * @param message mensagem a ser enviada
     * @throws IOException caso conexão não esteja disponível
     */
    void sendMessage(String message) throws IOException;

    /**
     * Recupera última mensagem enviada, ou bloqueia caso nenhuma haver chego.
     * Desbloqueia após timeout, se configurado
     * @return mensagem recuperada
     * @throws IOException caso conexão esteja indisponível
     */
    String getMessage() throws IOException;

    /**
     * Retorna de soquete esta disponível para conexão
     * @return estado da conexão
     */
    boolean isConnected();

    /**
     * Retorna identificador da conexão
     * @return identificador (em geral valor da porta do lado do cliente)
     */
    int getId();

    /**
     * Realiza desconexão e finalizações necessários à conexão
     */
    void disconnect();
}
