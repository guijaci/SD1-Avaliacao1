package edu.utfpr.guilhermej.sisdist.network;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;

public class TcpSynchroServerSideClient {
        private TcpServer parent;
        private Socket clientSocket;
        private PrintWriter out;
        private Scanner in;

        private boolean executionEnable;

        public TcpSynchroServerSideClient(TcpServer parent, Socket clientSocket){
            this.parent = parent;
            this.clientSocket = clientSocket;

            try {
                out = new PrintWriter(clientSocket.getOutputStream());
                in = new Scanner(clientSocket.getInputStream());
            } catch (IOException e) {
                System.out.println("TCP Conection IO: "+e.getMessage());
            }
        }

        public String getMessage() throws IOException {
            if(!isConnected())
                throw new IOException("TCP Connection closed.");
            return in.nextLine();
        }

        public void sendMessage(String message) throws IOException{
            if(!isConnected())
                throw new IOException("TCP Connection closed.");
            out.println(message);
        }

        public void setTimeout(int timeout) throws SocketException {
            clientSocket.setSoTimeout(timeout);
        }

        public boolean isConnected() {
            return executionEnable && clientSocket.isConnected() && !clientSocket.isClosed();
        }

        public void disconnect() {
            executionEnable = false;
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.printf("TCP Connection Disconect: " + e.getMessage());
            }
        }

        public TcpServer getParent() {
            return parent;
        }

}
