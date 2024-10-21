package com.emailapp;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class NetworkUtils {
    private static ObjectOutputStream getOutputStream(Socket socket) throws IOException {
        OutputStream os = socket.getOutputStream();
        if (os instanceof ObjectOutputStream) {
            return (ObjectOutputStream) os;
        } else {
            return new ObjectOutputStream(os);
        }
    }

    private static ObjectInputStream getInputStream(Socket socket) throws IOException {
        InputStream is = socket.getInputStream();
        if (is instanceof ObjectInputStream) {
            return (ObjectInputStream) is;
        } else {
            return new ObjectInputStream(is);
        }
    }

    public static void sendObject(Socket socket, Object obj) throws IOException {
        if (socket.isClosed()) {
            throw new SocketException("Socket is closed");
        }
        ObjectOutputStream out = getOutputStream(socket);
        out.writeObject(obj);
        out.flush();
        // Non chiudere lo stream qui
    }

    public static Object receiveObject(Socket socket) throws IOException, ClassNotFoundException {
        if (socket.isClosed()) {
            throw new SocketException("Socket is closed");
        }
        try {
            ObjectInputStream in = getInputStream(socket);
            return in.readObject();
        } catch (EOFException e) {
            throw new SocketException("Connection closed while reading");
        } catch (SocketTimeoutException e) {
            throw new SocketException("Timeout while waiting for server response");
        }
    }
}