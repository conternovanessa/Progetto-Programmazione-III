package com.emailapp.common;

import java.io.*;
import java.net.Socket;

public class NetworkUtils {
    public static void sendObject(Socket socket, Object obj) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            out.writeObject(obj);
        }
    }

    public static Object receiveObject(Socket socket) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            return in.readObject();
        }
    }

    public static void sendString(Socket socket, String message) throws IOException {
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(message);
        }
    }

    public static String receiveString(Socket socket) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            return in.readLine();
        }
    }
}