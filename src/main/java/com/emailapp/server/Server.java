package com.emailapp.server;

import com.emailapp.common.NetworkUtils;
import com.emailapp.server.controller.ServerController;
import com.emailapp.client.model.Email;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {
    private static final int PORT = 5000;
    private static final int MAX_BIND_ATTEMPTS = 5;
    private static final int BIND_RETRY_DELAY = 1000; // 1 second

    private final ExecutorService executorService;
    private final ServerController serverController;
    private ServerSocket serverSocket;
    private volatile boolean running;  // Usare volatile per garantire coerenza tra thread
    private final Object lock = new Object(); // Per sincronizzare avvio/stop

    public Server(ServerController serverController) {
        this.serverController = serverController;
        this.executorService = Executors.newCachedThreadPool();
    }

    public void start() {
        synchronized (lock) {
            if (running) {
                return;  // Evita di avviare il server se è già in esecuzione
            }
            running = true;
        }

        executorService.submit(() -> {
            int bindAttempts = 0;
            while (bindAttempts < MAX_BIND_ATTEMPTS && isRunning()) {
                try {
                    serverSocket = new ServerSocket();
                    serverSocket.setReuseAddress(true);  // Consente di riutilizzare la porta subito
                    serverSocket.bind(new InetSocketAddress(PORT));  // Associa la porta
                    serverController.logEvent("Server started on port " + PORT);
                    listenForConnections();  // Inizia ad ascoltare le connessioni
                    break;  // Esce dal ciclo se il server è avviato correttamente
                } catch (BindException e) {
                    bindAttempts++;
                    if (bindAttempts >= MAX_BIND_ATTEMPTS) {
                        serverController.logEvent("Failed to start server: Address already in use after " + MAX_BIND_ATTEMPTS + " attempts.");
                        stop();  // Ferma il server in caso di errore di bind definitivo
                        return;
                    }
                    serverController.logEvent("Address already in use. Retrying in " + BIND_RETRY_DELAY / 1000 + " seconds...");
                    try {
                        Thread.sleep(BIND_RETRY_DELAY);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } catch (IOException e) {
                    serverController.logEvent("Server error: " + e.getMessage());
                    stop();  // Arresta il server in caso di errore
                }
            }
        });
    }

    private void listenForConnections() {
        while (isRunning()) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleClient(clientSocket);
            } catch (IOException e) {
                if (isRunning()) {
                    serverController.logEvent("Error accepting client: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        executorService.submit(() -> {
            try (clientSocket) {
                String command = (String) NetworkUtils.receiveObject(clientSocket);

                switch (command) {
                    case "SEND_EMAIL":
                        handleSendEmail(clientSocket);
                        break;
                    case "FETCH_NEW_EMAILS":
                        handleFetchNewEmails(clientSocket);
                        break;
                    case "DELETE_EMAIL":
                        handleDeleteEmail(clientSocket);
                        break;
                    case "PING":
                        NetworkUtils.sendObject(clientSocket, "PONG");
                        break;
                    default:
                        NetworkUtils.sendObject(clientSocket, "INVALID_COMMAND");
                }
            } catch (IOException | ClassNotFoundException e) {
                serverController.logEvent("Error handling client: " + e.getMessage());
            }
        });
    }

    private void handleSendEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        Email email = (Email) NetworkUtils.receiveObject(clientSocket);
        serverController.logEvent("Email received: " + email.getSubject());
        NetworkUtils.sendObject(clientSocket, "SUCCESS");
    }

    private void handleFetchNewEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        String emailAddress = (String) NetworkUtils.receiveObject(clientSocket);
        serverController.logEvent("Fetching new emails for: " + emailAddress);
        List<Email> newEmails = getNewEmails(emailAddress);
        NetworkUtils.sendObject(clientSocket, newEmails);
    }

    private void handleDeleteEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        String emailId = (String) NetworkUtils.receiveObject(clientSocket);
        serverController.logEvent("Deleting email with ID: " + emailId);
        NetworkUtils.sendObject(clientSocket, "SUCCESS");
    }

    private List<Email> getNewEmails(String emailAddress) {
        // Simulazione di nuove email
        return new ArrayList<>();
    }

    public void stop() {
        synchronized (lock) {
            if (!running) {
                return;  // Evita di fermare il server se non è in esecuzione
            }
            running = false;  // Impedisce nuove connessioni
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();  // Chiude il ServerSocket
                serverController.logEvent("Server socket closed.");
            }
        } catch (IOException e) {
            serverController.logEvent("Error closing server socket: " + e.getMessage());
        }

        executorService.shutdownNow();  // Ferma tutti i thread esecutivi
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                serverController.logEvent("ExecutorService did not terminate in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        serverController.logEvent("Server stopped.");
    }

    private boolean isRunning() {
        synchronized (lock) {
            return running;
        }
    }
}
