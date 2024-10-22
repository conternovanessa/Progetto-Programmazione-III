package com.emailapp.server;

import com.emailapp.NetworkUtils;
import com.emailapp.client.Email;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Optional;
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
        this.mailServer = new MailServer(this);
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
        ObjectInputStream inputStream = null;
        boolean isAbnormalDisconnection = false;

        try {
            inputStream = new ObjectInputStream(clientSocket.getInputStream());
            clientSocket.setSoTimeout(30000); // 30 seconds timeout

            while (!clientSocket.isClosed()) {
                try {
                    String command = (String) inputStream.readObject();
                    if (command == null) {
                        break;
                    }

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
                            logEvent("Unknown command received: " + command);
                            break;
                    }
                } catch (SocketException se) {
                    // Normal disconnection scenarios - don't log these
                    if (se.getMessage().contains("Connection reset") ||
                            se.getMessage().contains("Socket closed") ||
                            se.getMessage().contains("Read timed out")) {
                        break;
                    }
                    // Unexpected socket errors should be logged
                    isAbnormalDisconnection = true;
                    logEvent("Unexpected socket error: " + se.getMessage());
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            // Only log abnormal disconnections or unexpected errors
            if (isRunning && !(e instanceof EOFException)) {
                isAbnormalDisconnection = true;
                logEvent("Error in client connection: " + e.getMessage());
            }
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                    // Log only abnormal disconnections
                    if (isAbnormalDisconnection) {
                        logEvent("Client connection closed after error");
                    }
                }
            } catch (IOException e) {
                if (isRunning) {
                    logEvent("Error while closing client resources: " + e.getMessage());
                }
            }
        }
    }

    private void handleSendEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        Email email = (Email) NetworkUtils.receiveObject(clientSocket);
        mailServer.sendEmail(email);
        NetworkUtils.sendObject(clientSocket, "SUCCESS");
        //logEvent("Email sent from " + email.getSender() + " to " + email.getRecipients());
    }

    private void handleFetchNewEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        String recipient = (String) NetworkUtils.receiveObject(clientSocket);
        List<Email> newEmails = mailServer.getNewEmails(recipient);
        NetworkUtils.sendObject(clientSocket, newEmails);
    }

    private void handleDeleteEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        Long emailId = null;
        String userEmail = null;
        boolean success = false;

        try {
            emailId = (Long) NetworkUtils.receiveObject(clientSocket);
            userEmail = (String) NetworkUtils.receiveObject(clientSocket);

            success = mailServer.deleteEmail(Math.toIntExact(emailId), userEmail);

            NetworkUtils.sendObject(clientSocket, success);

            logEvent("Email deletion " + (success ? "successful" : "failed") + " for ID: " + emailId + " and user: " + userEmail);
        } catch (Exception e) {
            logEvent("Error during email deletion: " + e.getMessage());
            NetworkUtils.sendObject(clientSocket, false);
        } finally {
            // Ensure we always send a response, even if an exception occurred
            if (emailId == null || userEmail == null) {
                NetworkUtils.sendObject(clientSocket, false);
            }
        }
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

        // Notify the view controller that the server has stopped
        Platform.runLater(() -> viewController.onServerStopped());
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

    public void handleStopServer() {
        if (!isRunning) {
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Stop Server");
        confirmAlert.setHeaderText("Vuoi davvero chiudere il server?");
        confirmAlert.setContentText("Questa operazione disconnetterà tutti i client attualmente connessi");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            stopServer();

            Alert infoAlert = new Alert(Alert.AlertType.INFORMATION);
            infoAlert.setTitle("Server chiuso");
            infoAlert.setHeaderText(null);
            infoAlert.setContentText("Il server è stato chiuso correttamente. Tutti i client sono stati disconnessi");
            infoAlert.showAndWait();
        }
    }


}
