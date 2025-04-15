package com.server.model;

import java.io.OutputStream;
import java.net.Socket;

public class ClientInfo {
    public int id;
    public String name;
    public Socket socket;
    public OutputStream out;

    public ClientInfo(int id, OutputStream out, String name) {
        this.id = id;
        this.out = out;
        this.name = name;
    }
}
