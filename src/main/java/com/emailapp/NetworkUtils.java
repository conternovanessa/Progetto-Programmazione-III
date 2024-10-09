package com.emailapp;

import java.io.*;
import java.net.Socket;

public class NetworkUtils {
    public static void sendObject(Socket socket, Object obj) throws IOException {
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        out.writeObject(obj);
        out.flush();
    }

    public static Object receiveObject(Socket socket) throws IOException, ClassNotFoundException {
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        return in.readObject();
    }
}
