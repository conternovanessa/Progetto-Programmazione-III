package com.emailapp.util;

import com.emailapp.client.model.Email;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

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
            throw new SocketException("Socket chiusa");
        }
        ObjectOutputStream out = getOutputStream(socket);
        out.writeObject(obj);
        out.flush();
    }

    public static Object receiveObject(Socket socket) throws IOException, ClassNotFoundException {
        if (socket.isClosed()) {
            throw new SocketException("Socket chiusa");
        }
        try {
            ObjectInputStream in = getInputStream(socket);
            return in.readObject();
        } catch (EOFException e) {
            throw new SocketException("Connessione chiusa durante la lettura");
        } catch (SocketTimeoutException e) {
            throw new SocketException("Timeout durante l'attesa della risposta del server");
        }
    }

    public static List<Email> fetchEmails(String serverAddress, int serverPort, String emailAddress) throws IOException {
        try (Socket socket = new Socket(serverAddress, serverPort)) {
            sendObject(socket, "FETCH_EMAILS");
            sendObject(socket, emailAddress);

            try {
                Object response = receiveObject(socket);
                if (response instanceof List<?>) {
                    return (List<Email>) response;
                }
                return new ArrayList<>();
            } catch (ClassNotFoundException e) {
                throw new IOException("Error receiving emails from server", e);
            }
        }
    }

}