package com.emailapp.server.controller;

import com.emailapp.client.model.Email;
import com.emailapp.server.model.MailServer;
import com.emailapp.util.NetworkUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Button;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ServerController {
    private final MailServer mailServer;
    private ExecutorService executorService;
    private ServerSocket serverSocket;
    private boolean isRunning;
    private static final int DEFAULT_PORT = 5000;
    private Set<Integer> notifiedEmailIds = new HashSet<>();
    private Map<String, Set<Integer>> notifiedEmailsPerClient = new HashMap<>();

    @FXML private Label portLabel;
    @FXML private Button startStopButton;
    @FXML private TextArea logTextArea;

    public ServerController() {
        this.mailServer = new MailServer(this);
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

        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            mailServer.loadExistingEmails();
            executorService.submit(this::acceptConnections);
            logEvent("Server avviato sulla porta " + port);
        } catch (IOException e) {
            logEvent("Errore avvio server: " + e.getMessage());
        }
    }

    private void acceptConnections() {
        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (isRunning) {
                    logEvent("Errore connessione client: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try (ObjectInputStream inputStream = new ObjectInputStream(clientSocket.getInputStream())) {
            clientSocket.setSoTimeout(30000);

            String command = (String) inputStream.readObject();
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
                    logEvent("Comando sconosciuto: " + command);
            }
        } catch (SocketException se) {
            if (!se.getMessage().contains("Socket closed")) {
                logEvent("Errore socket: " + se.getMessage());
            }
        } catch (EOFException eof) {
            // Client disconnesso normalmente
        } catch (Exception e) {
            logEvent("Errore gestione client: " + e.getMessage());
        } finally {
            closeClientSocket(clientSocket);
        }
    }

    private void handleSendEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        Email email = (Email) NetworkUtils.receiveObject(clientSocket);
        try {
            mailServer.sendEmail(email);
            NetworkUtils.sendObject(clientSocket, "OK");
            logEvent("✅ Email inviata: " + email.getSender() + " → " + email.getRecipients());
        } catch (Exception e) {
            NetworkUtils.sendObject(clientSocket, "ERROR");
            logEvent("❌ Invio fallito da: " + email.getSender() + " - Errore: " + e.getMessage());
        }
    }


    private void handleFetchNewEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        String recipient = (String) NetworkUtils.receiveObject(clientSocket);
        List<Email> newEmails = mailServer.getNewEmails(recipient);

        // Inizializza il set per il client se non esiste
        notifiedEmailsPerClient.putIfAbsent(recipient, new HashSet<>());
        Set<Integer> clientNotifiedEmails = notifiedEmailsPerClient.get(recipient);

        // Filtra le email non notificate per questo specifico client
        List<Email> unnotifiedEmails = newEmails.stream()
                .filter(email -> !clientNotifiedEmails.contains(email.getId()))
                .filter(email -> email.getRecipients().contains(recipient))
                .collect(Collectors.toList());

        if (!unnotifiedEmails.isEmpty()) {
            unnotifiedEmails.forEach(email -> clientNotifiedEmails.add(email.getId()));
            logEvent("📨 Recuperate " + unnotifiedEmails.size() + " email per: " + recipient);
        }

        NetworkUtils.sendObject(clientSocket, newEmails);
    }


    private void handleDeleteEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        int emailId = (int) NetworkUtils.receiveObject(clientSocket);
        String requestingUser = (String) NetworkUtils.receiveObject(clientSocket);

        boolean deleted = mailServer.deleteEmail(emailId, requestingUser);
        NetworkUtils.sendObject(clientSocket, deleted ? "OK" : "ERROR");
        logEvent(deleted ?
                "🗑️ Email " + emailId + " eliminata da: " + requestingUser :
                "❌ Eliminazione " + emailId + " fallita per: " + requestingUser);
    }

    private void closeClientSocket(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logEvent("Errore chiusura socket: " + e.getMessage());
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

    public void stopServer() {
        if (!isRunning) return;

        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            executorService.shutdownNow();
            executorService = Executors.newCachedThreadPool();
            logEvent("Server arrestato");
        } catch (IOException e) {
            logEvent("Errore arresto server: " + e.getMessage());
        }
        Platform.runLater(() -> startStopButton.setText("Start Server"));
    }

    @FXML
    public void handleStopServer() {
        if (!isRunning) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Arresto Server");
        alert.setHeaderText("Conferma arresto server");
        alert.setContentText("Tutti i client verranno disconnessi. Continuare?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                stopServer();
                showServerStoppedAlert();
            }
        });
    }

    private void showServerStoppedAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Server Arrestato");
        alert.setHeaderText(null);
        alert.setContentText("Il server è stato arrestato correttamente");
        alert.show();
    }

    private void updateButtonState() {
        startStopButton.setText(isRunning ? "Stop Server" : "Start Server");
    }

    public void logEvent(String message) {
        Platform.runLater(() -> logTextArea.appendText(message + "\n"));
    }

    public MailServer getMailServer() {
        return mailServer;
    }

    public boolean isRunning() {
        return isRunning;
    }
}
