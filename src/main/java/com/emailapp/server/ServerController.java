package com.emailapp.server;

import com.emailapp.NetworkUtils;
import com.emailapp.client.Email;
import javafx.application.Platform;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerController {
    private final MailServer mailServer;
    private ExecutorService executorService;
    private ServerSocket serverSocket;
    private boolean isRunning;
    private final ServerViewController viewController;
    private static final int DEFAULT_PORT = 5000;

    public ServerController(ServerViewController viewController) {
        this.mailServer = new MailServer();
        this.viewController = viewController;
        this.executorService = Executors.newCachedThreadPool();
        startServer(DEFAULT_PORT); // Start the server automatically
    }

    public void startServer(int port) {
        if (isRunning) {
            return;
        }
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            executorService.submit(this::acceptConnections);
            logEvent("Server started on port " + port);
        } catch (IOException e) {
            logEvent("Failed to start server: " + e.getMessage());
        }
    }

    private void acceptConnections() {
        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (isRunning) {
                    logEvent("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }


    private void handleClient(Socket clientSocket) {
        try {
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
                    logEvent("Unknown command: " + command);
            }
        } catch (Exception e) {
            logEvent("Error handling client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logEvent("Error closing client socket: " + e.getMessage());
            }
        }
    }

    private void handleSendEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        Email email = (Email) NetworkUtils.receiveObject(clientSocket);
        mailServer.sendEmail(email);
        NetworkUtils.sendObject(clientSocket, "SUCCESS");
        logEvent("Email sent from " + email.getSender() + " to " + email.getRecipients());
    }

    private void handleFetchNewEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        String recipient = (String) NetworkUtils.receiveObject(clientSocket);
        List<Email> newEmails = mailServer.getNewEmails(recipient);
        NetworkUtils.sendObject(clientSocket, newEmails);
    }

    private void handleDeleteEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        String emailId = (String) NetworkUtils.receiveObject(clientSocket);
        boolean success = mailServer.deleteEmail(emailId);
        NetworkUtils.sendObject(clientSocket, success ? "SUCCESS" : "FAILURE");
        logEvent("Email deletion " + (success ? "successful" : "failed") + " for ID: " + emailId);
    }

    public void stopServer() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logEvent("Error closing server socket: " + e.getMessage());
        }
        executorService.shutdownNow();
        executorService = Executors.newCachedThreadPool(); // Create a new ExecutorService for future use
        logEvent("Server stopped");
    }

    public void logEvent(String message) {
        Platform.runLater(() -> viewController.logEvent(message));
    }

    public MailServer getMailServer() {
        return mailServer;
    }

    public boolean isRunning() {
        return isRunning;
    }
}
