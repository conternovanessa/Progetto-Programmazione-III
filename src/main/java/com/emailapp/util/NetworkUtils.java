package com.emailapp.util;

import com.emailapp.util.Email;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkUtils {
    private static final int SOCKET_TIMEOUT = 30000;

    // Cache degli stream per socket
    private static final Map<Socket, ObjectOutputStream> outputStreams = new ConcurrentHashMap<>();
    private static final Map<Socket, ObjectInputStream> inputStreams = new ConcurrentHashMap<>();

    private static ObjectOutputStream getOutputStream(Socket socket) throws IOException {
        return outputStreams.computeIfAbsent(socket, k -> {
            try {
                socket.setSoTimeout(SOCKET_TIMEOUT);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                return out;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static ObjectInputStream getInputStream(Socket socket) throws IOException {
        return inputStreams.computeIfAbsent(socket, k -> {
            try {
                return new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void sendObject(Socket socket, Object obj) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new SocketException("Socket non valida o chiusa");
        }

        ObjectOutputStream out = getOutputStream(socket);
        out.writeObject(obj);
        out.flush();
    }

    public static Object receiveObject(Socket socket) throws IOException, ClassNotFoundException {
        if (socket == null || socket.isClosed()) {
            throw new SocketException("Socket non valida o chiusa");
        }

        ObjectInputStream in = getInputStream(socket);
        return in.readObject();
    }

    public static void sendAndReceive(Socket socket, Object request, ResponseHandler handler)
            throws IOException, ClassNotFoundException {
        if (socket == null || socket.isClosed()) {
            throw new SocketException("Socket non valida o chiusa");
        }

        ObjectOutputStream out = getOutputStream(socket);
        ObjectInputStream in = getInputStream(socket);

        out.writeObject(request);
        out.flush();
        handler.handle(in);
    }

    public static List<Email> fetchEmails(String serverAddress, int serverPort, String emailAddress)
            throws IOException {
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
            cleanupSocket(socket);
        }
    }

    public static void cleanupSocket(Socket socket) {
        if (socket != null) {
            try {
                ObjectOutputStream out = outputStreams.remove(socket);
                if (out != null) {
                    out.close();
                }
                ObjectInputStream in = inputStreams.remove(socket);
                if (in != null) {
                    in.close();
                }
                socket.close();
            } catch (IOException e) {
                System.err.println("Errore nella chiusura dei socket: " + e.getMessage());
            }
        }
    }

    @FunctionalInterface
    public interface ResponseHandler {
        void handle(ObjectInputStream in) throws IOException, ClassNotFoundException;
    }
}