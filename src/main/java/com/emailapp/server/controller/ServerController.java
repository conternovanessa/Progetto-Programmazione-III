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
        try {
            clientSocket.setSoTimeout(30000); // 30 secondi timeout
            String command = (String) NetworkUtils.receiveObject(clientSocket);

            switch (command) {
                case "SEND_EMAIL":
                    handleSendEmail(clientSocket);
                    break;
                case "PREPARE_REPLY":
                    handlePrepareReply(clientSocket);
                    break;
                case "PREPARE_REPLY_ALL":
                    handlePrepareReplyAll(clientSocket);
                    break;
                case "PREPARE_FORWARD":
                    handlePrepareForward(clientSocket);
                    break;
                case "DELETE_EMAIL":
                    handleDeleteEmail(clientSocket);
                    break;
                case "FETCH_EMAILS":
                    handleFetchEmails(clientSocket);
                    break;
                case "PING":
                    handlePing(clientSocket);
                    break;
                default:
                    logEvent("❓ Comando sconosciuto: " + command);
                    NetworkUtils.sendObject(clientSocket, "ERROR: Comando sconosciuto");
            }

        } catch (Exception e) {
            logEvent("❌ Errore gestione client: " + e.getMessage());
        } finally {
            closeClientSocket(clientSocket);
        }
    }

    private void handleSendEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        Email newEmail = (Email) NetworkUtils.receiveObject(clientSocket);
        String senderEmail = (String) NetworkUtils.receiveObject(clientSocket);

        try {
            mailServer.sendEmail(newEmail);
            NetworkUtils.sendObject(clientSocket, "OK");
            logEvent("✅ Email inviata da: " + senderEmail);
        } catch (Exception e) {
            NetworkUtils.sendObject(clientSocket, "ERROR: " + e.getMessage());
            logEvent("❌ Invio fallito da: " + senderEmail);
        }
    }

    private void handlePrepareReply(Socket clientSocket) throws IOException, ClassNotFoundException {
        int replyEmailId = (int) NetworkUtils.receiveObject(clientSocket);
        try {
            boolean canReply = mailServer.canAccessEmail(replyEmailId);
            NetworkUtils.sendObject(clientSocket, canReply ? "OK" : "ERROR");
            logEvent("🔄 Preparazione risposta per email: " + replyEmailId);
        } catch (Exception e) {
            NetworkUtils.sendObject(clientSocket, "ERROR");
            logEvent("❌ Errore preparazione risposta per email: " + replyEmailId);
        }
    }

    private void handlePrepareReplyAll(Socket clientSocket) throws IOException, ClassNotFoundException {
        int replyAllEmailId = (int) NetworkUtils.receiveObject(clientSocket);
        try {
            boolean canReplyAll = mailServer.canAccessEmail(replyAllEmailId);
            NetworkUtils.sendObject(clientSocket, canReplyAll ? "OK" : "ERROR");
            logEvent("🔄 Preparazione risposta a tutti per email: " + replyAllEmailId);
        } catch (Exception e) {
            NetworkUtils.sendObject(clientSocket, "ERROR");
            logEvent("❌ Errore preparazione risposta a tutti per email: " + replyAllEmailId);
        }
    }

    private void handlePrepareForward(Socket clientSocket) throws IOException, ClassNotFoundException {
        int forwardEmailId = (int) NetworkUtils.receiveObject(clientSocket);
        try {
            boolean canForward = mailServer.canAccessEmail(forwardEmailId);
            NetworkUtils.sendObject(clientSocket, canForward ? "OK" : "ERROR");
            logEvent("↪ Preparazione inoltro per email: " + forwardEmailId);
        } catch (Exception e) {
            NetworkUtils.sendObject(clientSocket, "ERROR");
            logEvent("❌ Errore preparazione inoltro per email: " + forwardEmailId);
        }
    }

    private void handleDeleteEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        int deleteEmailId = (int) NetworkUtils.receiveObject(clientSocket);
        String requestingUser = (String) NetworkUtils.receiveObject(clientSocket);

        boolean deleted = mailServer.deleteEmail(deleteEmailId, requestingUser);
        NetworkUtils.sendObject(clientSocket, deleted ? "OK" : "ERROR");
        logEvent(deleted ?
                "🗑 Email " + deleteEmailId + " eliminata da: " + requestingUser :
                "❌ Eliminazione email " + deleteEmailId + " fallita per: " + requestingUser);
    }

    private void handleFetchEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        String recipient = (String) NetworkUtils.receiveObject(clientSocket);
        List<Email> emails = mailServer.getEmailsForUser(recipient);
        NetworkUtils.sendObject(clientSocket, emails);
        logEvent("📨 Inviate " + emails.size() + " email a: " + recipient);
    }

    private void handlePing(Socket clientSocket) throws IOException {
        NetworkUtils.sendObject(clientSocket, "PONG");
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

    public boolean isRunning() {
        return isRunning;
    }
}
