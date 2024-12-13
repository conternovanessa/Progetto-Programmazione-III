package com.emailapp.server.controller;

import com.emailapp.server.model.ServerObserver;
import com.emailapp.util.Email;
import com.emailapp.server.model.ClientPorts;
import com.emailapp.server.model.MailServer;
import com.emailapp.util.NetworkUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Button;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ServerController implements ServerObserver {
    private final MailServer mailServer;
    private ExecutorService executorService;
    private ServerSocket serverSocket;
    private volatile boolean isRunning;
    private static final int DEFAULT_PORT = 5000;
    private static final int SOCKET_TIMEOUT = 30000;
    private final Set<String> initialFetchDone = Collections.synchronizedSet(new HashSet<>());
    private final Object socketLock = new Object();

    @FXML private Label portLabel;
    @FXML private Button startStopButton;
    @FXML private TextArea logTextArea;

    public ServerController() {
        this.mailServer = new MailServer(this);
        this.mailServer.addObserver(this);
        this.executorService = Executors.newCachedThreadPool();
    }

    @FXML
    private void initialize() {
        portLabel.setText(String.valueOf(DEFAULT_PORT));
        startServer(DEFAULT_PORT);
        updateButtonState();
    }
    public void startServer(int port) {
        if (isRunning) return;

        stopServer();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newCachedThreadPool();
        }

        executorService.submit(() -> {
            try {
                serverSocket = new ServerSocket(port);
                serverSocket.setReuseAddress(true);
                isRunning = true;
                mailServer.loadExistingEmails();
                onServerStateChanged(true); // This will handle both logging and button state
                acceptConnections();
            } catch (IOException e) {
                logEvent("❌ Errore avvio server: " + e.getMessage());
            }
        });
    }

    public void stopServer() {
        if (!isRunning) return;

        isRunning = false;

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logEvent("Errore chiusura socket: " + e.getMessage());
            }
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        onServerStateChanged(false); // This will handle both logging and button state
    }

    @Override
    public void onServerStateChanged(boolean isRunning) {
        Platform.runLater(() -> {
            logEvent(isRunning ? "✅ Server avviato" : "⛔ Server arrestato");
            startStopButton.setText(isRunning ? "Stop Server" : "Start Server");
        });
    }


    private void acceptConnections() {
        while (isRunning) {
            try {
                Socket clientSocket;
                synchronized(socketLock) {
                    if (!isRunning) break;
                    clientSocket = serverSocket.accept();
                }
                executorService.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (isRunning) {
                    logEvent("❌ Errore connessione client: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            clientSocket.setSoTimeout(SOCKET_TIMEOUT);
            String command = (String) NetworkUtils.receiveObject(clientSocket);
            String requestingUser = null;

            if (requiresRequestingUser(command)) {
                requestingUser = (String) NetworkUtils.receiveObject(clientSocket);
            }

            processClientCommand(command, requestingUser, clientSocket);
        } catch (Exception e) {
            logEvent("❌ Errore gestione client: " + e.getMessage());
            try {
                sendError(clientSocket, "Errore interno del server");
            } catch (IOException ignored) {}
        } finally {
            closeClientSocket(clientSocket);
        }
    }

    private void processClientCommand(String command, String requestingUser, Socket clientSocket) throws IOException, ClassNotFoundException {
        switch (command) {
            case "REQUEST_WRITE_SOCKET" -> handleWriteSocketRequest(clientSocket);
            case "CHECK_COMPOSE" -> NetworkUtils.sendObject(clientSocket, "OK");
            case "SEND_EMAIL" -> handleSendEmail(clientSocket);
            case "DELETE_EMAIL" -> handleDeleteEmail(clientSocket, requestingUser);
            case "CHECK_NEW_EMAILS" -> handleCheckNewEmails(clientSocket);
            case "FETCH_SENT_EMAILS" -> handleFetchSentEmails(clientSocket);
            case "FETCH_RECEIVED_EMAILS" -> handleFetchReceivedEmails(clientSocket);
            case "FETCH_EMAILS" -> handleFetchEmails(clientSocket);
            case "PING" -> handlePing(clientSocket);
            default -> {
                logEvent("❓ Comando sconosciuto: " + command);
                sendError(clientSocket, "Comando sconosciuto");
            }
        }
    }


    private boolean requiresRequestingUser(String command) {
        return command.equals("REPLY") ||
                command.equals("REPLY_ALL") ||
                command.equals("FORWARD") ||
                command.equals("DELETE_EMAIL");
    }

    private void handleWriteSocketRequest(Socket clientSocket) throws IOException, ClassNotFoundException {
        String userEmail = (String) NetworkUtils.receiveObject(clientSocket);
        logEvent("📝 Richiesta apertura socket di scrittura da: " + userEmail);
        int dedicatedPort = ClientPorts.getPortForClient(userEmail);

        try {
            ServerSocket dedicatedServerSocket = new ServerSocket(dedicatedPort);
            logEvent("🔌 Creata socket dedicata sulla porta: " + dedicatedPort);
            NetworkUtils.sendObject(clientSocket, dedicatedPort);

            Socket dedicatedSocket = dedicatedServerSocket.accept();
            Email newEmail = (Email) NetworkUtils.receiveObject(dedicatedSocket);
            String senderEmail = (String) NetworkUtils.receiveObject(dedicatedSocket);

            try {
                mailServer.sendEmail(newEmail);
                NetworkUtils.sendObject(dedicatedSocket, "OK");
            } catch (Exception e) {
                NetworkUtils.sendObject(dedicatedSocket, "ERROR: " + e.getMessage());
                logEvent("❌ Errore durante l'invio dell'email");
            }

            dedicatedSocket.close();
            dedicatedServerSocket.close();
        } catch (IOException e) {
            NetworkUtils.sendObject(clientSocket, -1);
            logEvent("❌ Errore apertura socket dedicata per " + userEmail);
        }
    }

    private void handleCheckNewEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        String recipient = (String) NetworkUtils.receiveObject(clientSocket);
        List<Email> newEmails = mailServer.retrieveQueuedEmails(recipient);
        NetworkUtils.sendObject(clientSocket, newEmails);
    }

    private void handleSendEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        Email newEmail = (Email) NetworkUtils.receiveObject(clientSocket);
        String senderEmail = (String) NetworkUtils.receiveObject(clientSocket);

        try {
            mailServer.sendEmail(newEmail);
            NetworkUtils.sendObject(clientSocket, "OK");
        } catch (Exception e) {
            NetworkUtils.sendObject(clientSocket, "ERROR: " + e.getMessage());
            logEvent("❌ Invio fallito da: " + senderEmail);
        }
    }

    private void handleDeleteEmail(Socket clientSocket, String requestingUser) throws IOException, ClassNotFoundException {
        Object idObj = NetworkUtils.receiveObject(clientSocket);
        int emailId = Integer.parseInt(idObj.toString());

        logEvent("Richiesta al server di eliminare una email con Id: " + emailId +" per l'account: "+ requestingUser);
        boolean deleted = mailServer.deleteEmail(emailId, requestingUser);
        NetworkUtils.sendObject(clientSocket, deleted ? "OK" : "ERROR");

        logEvent(deleted ?
                "🗑 Email " + emailId + " eliminata da: " + requestingUser :
                "❌ Eliminazione email " + emailId + " fallita per: " + requestingUser);
    }

    private void handleFetchSentEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        String sender = (String) NetworkUtils.receiveObject(clientSocket);
        if (!initialFetchDone.contains(sender)) {
            List<Email> sentEmails = mailServer.getEmailsFromSender(sender);
            initialFetchDone.add(sender);
        }
        NetworkUtils.sendObject(clientSocket, mailServer.getEmailsFromSender(sender));
    }

    private void handleFetchReceivedEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        String recipient = (String) NetworkUtils.receiveObject(clientSocket);
        if (!initialFetchDone.contains(recipient)) {
            List<Email> receivedEmails = mailServer.getEmailsForRecipient(recipient);
            logEvent("Caricate " + receivedEmails.size() + " email ricevute per: " + recipient);
            initialFetchDone.add(recipient);
        }
        NetworkUtils.sendObject(clientSocket, mailServer.getEmailsForRecipient(recipient));
    }

    private void handleFetchEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        String userEmail = (String) NetworkUtils.receiveObject(clientSocket);

        if (!initialFetchDone.contains(userEmail)) {
            List<Email> allEmails = mailServer.getEmailsForUser(userEmail);
            String emailCount = allEmails.size() == 1 ? "Caricata 1 email" : "Caricate " + allEmails.size() + " email";
            logEvent(emailCount + " per: " + userEmail);
            initialFetchDone.add(userEmail);
        }

        NetworkUtils.sendObject(clientSocket, mailServer.getEmailsForUser(userEmail));
    }



    private void handlePing(Socket clientSocket) throws IOException {
        NetworkUtils.sendObject(clientSocket, "PONG");
    }

    private void sendError(Socket clientSocket, String errorMessage) throws IOException {
        NetworkUtils.sendObject(clientSocket, "ERROR: " + errorMessage);
    }

    private void closeClientSocket(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logEvent("❌ Errore chiusura socket: " + e.getMessage());
        }
    }

    @FXML
    private void handleStartStop() {
        if (isRunning) {
            handleStopServer();
        } else {
            startServer(DEFAULT_PORT);
        }
        updateButtonState();
    }

    @FXML
    public void handleStopServer() {
        if (!isRunning) return;

        showConfirmationDialog(
                "Arresto Server",
                "Conferma arresto server",
                "Tutti i client verranno disconnessi. Continuare?",
                () -> {
                    stopServer();
                    showInformationDialog("Server Arrestato", null, "Il server è stato arrestato correttamente");
                }
        );
    }

    private void showConfirmationDialog(String title, String header, String content, Runnable onConfirm) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                onConfirm.run();
            }
        });
    }

    private void showInformationDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.show();
    }

    private void showErrorAlert(String title, String header, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            alert.show();
        });
    }

    private void updateButtonState() {
        Platform.runLater(() -> startStopButton.setText(isRunning ? "Stop Server" : "Start Server"));
    }

    public void logEvent(String message) {
        Platform.runLater(() -> logTextArea.appendText(message + "\n"));
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onEmailSent(Email email) {
        Platform.runLater(() ->
                logEvent("📤 Email inviata da: " + email.getSender() +
                        " a: " + String.join(", ", email.getRecipients()))
        );
    }

    @Override
    public void onEmailReceived(Email email) {
        Platform.runLater(() ->
                logEvent("📥 Email ricevuta da: " + email.getRecipients() +
                        " per: " + String.join(", ", email.getSender()))
        );
    }

    @Override
    public void onEmailDeleted(int emailId) {
        Platform.runLater(() ->
                logEvent("🗑️ Email " + emailId + " eliminata")
        );
    }

    public void shutdown() {
        try {
            // Stop accepting new connections
            stopServer();

            // Clear any pending operations
            if (executorService != null) {
                executorService.shutdownNow();
                try {
                    executorService.awaitTermination(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Clear resources
            if (mailServer != null) {
                mailServer.removeObserver(this);
            }

            Platform.runLater(() -> {
                if (logTextArea != null) {
                    logTextArea.clear();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}