package com.emailapp.util;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public class NetworkUtils {
    private static final int SOCKET_TIMEOUT = 30000; // 30 secondi timeout

    private static ObjectOutputStream createOutputStream(Socket socket) throws IOException {
        socket.setSoTimeout(SOCKET_TIMEOUT);
        return new ObjectOutputStream(socket.getOutputStream());
    }

    private static ObjectInputStream createInputStream(Socket socket) throws IOException {
        return new ObjectInputStream(socket.getInputStream());
    }

    public static void sendObject(Socket socket, Object obj) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new SocketException("Socket non valida o chiusa");
        }

        ObjectOutputStream out = null;
        try {
            out = createOutputStream(socket);
            out.writeObject(obj);
            out.flush();
        } finally {
            // Non chiudiamo l'output stream qui per permettere riutilizzo della socket
            if (out != null) {
                out.flush();
            }
        }
    }

    public static Object receiveObject(Socket socket) throws IOException, ClassNotFoundException {
        if (socket == null || socket.isClosed()) {
            throw new SocketException("Socket non valida o chiusa");
        }

        ObjectInputStream in = null;
        try {
            in = createInputStream(socket);
            return in.readObject();
        } catch (EOFException e) {
            throw new SocketException("Connessione terminata dal server");
        } catch (SocketTimeoutException e) {
            throw new SocketException("Timeout nella lettura dal server");
        }
    }

    public static List<Email> fetchEmails(String serverAddress, int serverPort, String emailAddress) throws IOException {
        Socket socket = null;
        try {
            socket = new Socket(serverAddress, serverPort);
            socket.setSoTimeout(SOCKET_TIMEOUT);

            sendObject(socket, "FETCH_EMAILS");
            sendObject(socket, emailAddress);

            Object response = receiveObject(socket);
            if (response instanceof List<?>) {
                return (List<Email>) response;
            }
            return new ArrayList<>();

        } catch (ClassNotFoundException e) {
            throw new IOException("Errore nella deserializzazione delle email", e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Log dell'errore
                }
            }
        }
    }
}