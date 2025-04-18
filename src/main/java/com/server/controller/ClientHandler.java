package com.server.controller;

import com.server.model.ClientInfo;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class ClientHandler extends Thread {
    private Socket socket;
    private int clientId;
    private Map<Socket, ClientInfo> clients;
    private OutputStream out;
    private InputStream in;
    private String clientName;

    private static final Gson gson = new Gson();

    public ClientHandler(Socket socket, int clientId, Map<Socket, ClientInfo> clients) {
        this.socket = socket;
        this.clientId = clientId;
        this.clients = clients;
    }

    public void run() {
        try {
            out = socket.getOutputStream();
            in = socket.getInputStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            String line, key = null;
            while (!(line = reader.readLine()).isEmpty()) {
                if (line.startsWith("Sec-WebSocket-Key: ")) {
                    key = line.substring(19);
                }
            }

            if (key != null) {
                String acceptKey = Base64.getEncoder().encodeToString(
                        MessageDigest.getInstance("SHA-1")
                                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                        .getBytes(StandardCharsets.UTF_8)));

                String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";

                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();

                clients.put(socket, new ClientInfo(clientId, out, clientName));
                

                while (true) {
                    byte[] message = decodeMessage(in);
                    if (message == null)
                        break;

                    String msg = new String(message, StandardCharsets.UTF_8);
                    System.out.println("Client " + clientId + " dice: " + msg);

                    @SuppressWarnings("unchecked")
                    Map<String, String> parsed = gson.fromJson(msg, Map.class);

                    if (parsed.containsKey("type")) {
                        String type = parsed.get("type");

                        if ("init".equals(type)) {
                            clientName = parsed.get("name");
                            clients.get(socket).name = clientName;
                            broadcastUserList();
                        } else if ("message".equals(type)) {
                            int toId = Integer.parseInt(parsed.get("to"));
                            String text = parsed.get("text");
                            sendMessageTo(toId, clientId, text);
                        } else if("setName".equals(type)) {
                            clientName = parsed.get("name");
                            clients.get(socket).name = clientName;
                            broadcastUserList();
                        } else if ("disconnect".equals(type)) {
                            System.out.println("Client " + clientId + " si è disconnesso.");
                            break;
                        } else if ("ice-candidate".equals(type) || "offer".equals(type) || "answer".equals(type)) {
                            String to = parsed.get("to");
                            parsed.put("from", String.valueOf(clientId)); // Mittente
                        
                            for (ClientInfo client : clients.values()) {
                                if (String.valueOf(client.id).equals(to)) {
                                    parsed.put("fromUser", String.valueOf(clientName));
                                    sendMessage(client.out, gson.toJson(parsed)); //scelgo questo perchè sendMessageTo manda già il tipo "message" ma a me serve altro tipo (offer, ice-candidate, answer)
                                    break;
                                }
                            }
                        } else if("reject".equals(type)){

                            String to = parsed.get("to");
                            parsed.put("from", String.valueOf(clientId)); // Mittente
                        
                            for (ClientInfo client : clients.values()) {
                                if (String.valueOf(client.id).equals(to)) {
                                    parsed.put("fromUser", String.valueOf(clientName));
                                    sendMessage(client.out, gson.toJson(parsed)); //scelgo questo perchè sendMessageTo manda già il tipo "message" ma a me serve altro tipo (reject)
                                    break;
                                }
                            }
                        }
                        
                        else {
                            System.out.println("Tipo di messaggio sconosciuto: " + type);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Client disconnesso: " + clientId);
        } finally {
            clients.remove(socket);
            broadcastUserList();
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    private void sendMessageTo(int toId, int fromId, String text) throws IOException {
        for (ClientInfo client : clients.values()) {
            if (client.id == toId) {
                Map<String, String> msg = new HashMap<>();
                msg.put("type", "message");
                msg.put("from", String.valueOf(fromId));
                msg.put("text", text);
                sendMessage(client.out, gson.toJson(msg));
                break;
            }
        }
    }

    private void broadcastUserList() {
        List<Map<String, String>> userList = new ArrayList<>();

        for (ClientInfo client : clients.values()) {
            Map<String, String> userInfo = new HashMap<>();
            userInfo.put("id", String.valueOf(client.id));
            userInfo.put("name", client.name);
            userList.add(userInfo);
        }

        Map<String, Object> json = new HashMap<>();
        json.put("type", "userList");
        json.put("users", userList);

        clients.values().forEach(c -> {
            try {
                Map<String, Object> personalJson = new HashMap<>(json);
                personalJson.put("id", c.id); // invia il suo id per il controllo e non mettere se stesso nella lista
                sendMessage(c.out, gson.toJson(personalJson));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }


    private byte[] decodeMessage(InputStream input) throws IOException {
        int b1 = input.read();
        int b2 = input.read();
        if (b1 == -1 || b2 == -1) return null;

        int payloadLength = b2 & 127;
        if (payloadLength == 126) {
            payloadLength = input.read() << 8 | input.read();
        } else if (payloadLength == 127) {
            for (int i = 0; i < 8; i++) input.read();
        }

        byte[] mask = input.readNBytes(4);
        byte[] data = input.readNBytes(payloadLength);

        for (int i = 0; i < data.length; i++) {
            data[i] ^= mask[i % 4];
        }

        return data;
    }

    private void sendMessage(OutputStream outputStream, String message) throws IOException {
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
        outputStream.write(0x81);
        if (msgBytes.length <= 125) {
            outputStream.write(msgBytes.length);
        } else if (msgBytes.length <= 65535) {
            outputStream.write(126);
            outputStream.write(msgBytes.length >> 8);
            outputStream.write(msgBytes.length & 0xFF);
        }
        outputStream.write(msgBytes);
        outputStream.flush();
    }
}
