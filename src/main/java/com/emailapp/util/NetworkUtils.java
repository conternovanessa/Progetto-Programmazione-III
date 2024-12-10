package com.emailapp.util;

import com.emailapp.util.Email;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public class NetworkUtils {
    private static final int SOCKET_TIMEOUT = 30000;

    private static ObjectOutputStream createOutputStream(Socket socket) throws IOException {
        socket.setSoTimeout(SOCKET_TIMEOUT);
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        out.flush(); // Important to flush header
        return out;
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
            if (out != null) {
                out.flush();
            }
        }
    }

    public static Object receiveObject(Socket socket) throws IOException, ClassNotFoundException {
        if (socket == null || socket.isClosed()) {
            throw new SocketException("Socket non valida o chiusa");
        }

        try {
            ObjectInputStream in = createInputStream(socket);
            return in.readObject();
        } catch (EOFException e) {
            throw new SocketException("Connessione terminata dal server");
        } catch (SocketTimeoutException e) {
            throw new SocketException("Timeout nella lettura dal server");
        }
    }

    public static void sendAndReceive(Socket socket, Object request, ResponseHandler handler)
            throws IOException, ClassNotFoundException {
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        try {
            out = createOutputStream(socket);
            in = createInputStream(socket);

            out.writeObject(request);
            out.flush();

            handler.handle(in);
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    public static List<Email> fetchEmails(String serverAddress, int serverPort, String emailAddress)
            throws IOException {
        try (Socket socket = new Socket(serverAddress, serverPort)) {
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
        }
    }

    private static void closeQuietly(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException e) {
                // log error
            }
        }
    }

    @FunctionalInterface
    public interface ResponseHandler {
        void handle(ObjectInputStream in) throws IOException, ClassNotFoundException;
    }
}
