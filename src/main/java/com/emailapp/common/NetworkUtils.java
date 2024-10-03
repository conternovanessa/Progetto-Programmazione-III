package com.emailapp.common;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class NetworkUtils {
    public static void sendObject(Socket socket, Object obj) throws Exception {
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        out.writeObject(obj);
    }

    public static Object receiveObject(Socket socket) throws Exception {
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        return in.readObject();
    }
}