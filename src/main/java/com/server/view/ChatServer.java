package com.server.view;

import com.server.controller.ClientHandler;
import com.server.model.ClientInfo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 8081;
    private static Map<Socket, ClientInfo> clients = new ConcurrentHashMap<>();
    private static int idCounter = 0;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server WebSocket avviato sulla porta " + PORT);

            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    int clientId = idCounter++;
                    ClientHandler handler = new ClientHandler(socket, clientId, clients);
                    handler.start();
                } catch (IOException e) {
                    System.err.println("Errore durante l'accettazione della connessione: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Errore durante l'avvio del server: " + e.getMessage());
        }
    }
}