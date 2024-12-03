package com.emailapp.server.controller;

import com.emailapp.client.model.Email;
import com.emailapp.server.model.MailServer;
import com.emailapp.util.EmailFileManager;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ServerController {
    private final MailServer mailServer;
    private ExecutorService executorService;
    private ServerSocket serverSocket;
    private volatile boolean isRunning;
    private static final int DEFAULT_PORT = 5000;
    private static final int SOCKET_TIMEOUT = 30000;// 30 secondi
    private Set<String> initialFetchDone = new HashSet<>();


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
            logEvent("✅ Server avviato sulla porta " + port);
        } catch (IOException e) {
            logEvent("❌ Errore avvio server: " + e.getMessage());
            showErrorAlert("Errore Server", "Impossibile avviare il server", e.getMessage());
        }
    }

    private void acceptConnections() {
        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
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

            switch (command) {
                case "REQUEST_WRITE_SOCKET":
                    handleWriteSocketRequest(clientSocket);
                    break;

                case "CHECK_COMPOSE":
                    NetworkUtils.sendObject(clientSocket, "OK");
                    break;

                case "MARK_AS_READ":
                    int emailId = (int) NetworkUtils.receiveObject(clientSocket);
                    String userEmail = (String) NetworkUtils.receiveObject(clientSocket);
                    try {
                        EmailFileManager.markEmailAsRead(emailId, userEmail);
                        NetworkUtils.sendObject(clientSocket, "OK");
                    } catch (IOException e) {
                        NetworkUtils.sendObject(clientSocket, "ERROR");
                    }
                    break;

                case "SEND_EMAIL":
                    handleSendEmail(clientSocket);
                    break;

                case "REPLY":
                    handleReply(clientSocket, requestingUser);
                    break;

                case "REPLY_ALL":
                    handleReplyAll(clientSocket, requestingUser);
                    break;

                case "FORWARD":
                    handleForward(clientSocket, requestingUser);
                    break;

                case "DELETE_EMAIL":
                    handleDeleteEmail(clientSocket, requestingUser);
                    break;

                case "CHECK_NEW_EMAILS":
                    handleCheckNewEmails(clientSocket);
                    break;

                case "FETCH_SENT_EMAILS":
                    handleFetchSentEmails(clientSocket);
                    break;

                case "FETCH_RECEIVED_EMAILS":
                    handleFetchReceivedEmails(clientSocket);
                    break;

                case "FETCH_EMAILS":
                    handleFetchEmails(clientSocket);
                    break;

                case "PING":
                    handlePing(clientSocket);
                    break;

                default:
                    logEvent("❓ Comando sconosciuto: " + command);
                    sendError(clientSocket, "Comando sconosciuto");
            }
        } catch (Exception e) {
            logEvent("❌ Errore gestione client: " + e.getMessage());
            try {
                sendError(clientSocket, "Errore interno del server");
            } catch (IOException ignored) {}
        } finally {
            closeClientSocket(clientSocket);
        }
    }

    private boolean requiresRequestingUser(String command) {
        return command.equals("REPLY") ||
                command.equals("REPLY_ALL") ||
                command.equals("FORWARD") ||
                command.equals("DELETE_EMAIL");
    }

    private void handleWriteSocketRequest(Socket clientSocket) throws IOException, ClassNotFoundException {
        logEvent("📝 Ricevuta richiesta apertura socket di scrittura");

        ServerSocket dedicatedServerSocket = new ServerSocket(0);
        int dedicatedPort = dedicatedServerSocket.getLocalPort();
        logEvent("🔌 Creata socket dedicata sulla porta: " + dedicatedPort);
        NetworkUtils.sendObject(clientSocket, dedicatedPort);

        Socket dedicatedSocket = dedicatedServerSocket.accept();
        logEvent("✅ Connessione stabilita sulla socket dedicata");

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
        logEvent("🔒 Socket dedicata chiusa dopo l'invio");
    }


    private void handleCheckNewEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        String recipient = (String) NetworkUtils.receiveObject(clientSocket);
        List<Email> newEmails = mailServer.retrieveQueuedEmails(recipient);
        NetworkUtils.sendObject(clientSocket, newEmails);
    }

    private void handleReply(Socket clientSocket, String requestingUser) throws IOException, ClassNotFoundException {
        try {
            int emailId = (int) NetworkUtils.receiveObject(clientSocket);
            Email replyEmail = mailServer.createReplyEmail(emailId, requestingUser);

            if (replyEmail == null) {
                NetworkUtils.sendObject(clientSocket, "ERROR");
                logEvent("❌ Email non trovata per risposta: ID " + emailId);
                return;
            }

            NetworkUtils.sendObject(clientSocket, "OK");
            NetworkUtils.sendObject(clientSocket, replyEmail);
            logEvent("📧 Template risposta creato per email: " + emailId);
        } finally {
            closeClientSocket(clientSocket);
        }
    }

    private void handleReplyAll(Socket clientSocket, String requestingUser) throws IOException, ClassNotFoundException {
        try {
            int emailId = (int) NetworkUtils.receiveObject(clientSocket);
            Email replyAllEmail = mailServer.createReplyAllEmail(emailId, requestingUser);

            if (replyAllEmail == null) {
                sendError(clientSocket, "Email non trovata o accesso negato");
                return;
            }

            NetworkUtils.sendObject(clientSocket, "OK");
            NetworkUtils.sendObject(clientSocket, replyAllEmail);
            logEvent("📧 Creata risposta a tutti per email: " + emailId);
        } finally {
            closeClientSocket(clientSocket);
        }
    }

    private void handleForward(Socket clientSocket, String requestingUser) throws IOException, ClassNotFoundException {
        try {
            int emailId = (int) NetworkUtils.receiveObject(clientSocket);
            Email forwardEmail = mailServer.createForwardEmail(emailId, requestingUser);

            if (forwardEmail == null) {
                sendError(clientSocket, "Email non trovata o accesso negato");
                return;
            }

            NetworkUtils.sendObject(clientSocket, "OK");
            NetworkUtils.sendObject(clientSocket, forwardEmail);
            logEvent("📧 Creato inoltro per email: " + emailId);
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









    private void handleFetchReceivedEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        String recipient = (String) NetworkUtils.receiveObject(clientSocket);
        List<Email> receivedEmails = mailServer.getEmailsForRecipient(recipient);
        NetworkUtils.sendObject(clientSocket, receivedEmails);

        if (!initialFetchDone.contains(recipient)) {
            logEvent("📥 Caricate " + receivedEmails.size() + " email ricevute per: " + recipient);
            initialFetchDone.add(recipient);
        }
    }

    private void handleFetchSentEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        String sender = (String) NetworkUtils.receiveObject(clientSocket);
        List<Email> sentEmails = mailServer.getEmailsFromSender(sender);
        NetworkUtils.sendObject(clientSocket, sentEmails);

        if (!initialFetchDone.contains(sender)) {
            logEvent("📤 Caricate " + sentEmails.size() + " email inviate da: " + sender);
        }
    }


    private void handleFetchEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        String userEmail = (String) NetworkUtils.receiveObject(clientSocket);
        List<Email> allEmails = mailServer.getEmailsForUser(userEmail);
        NetworkUtils.sendObject(clientSocket, allEmails);
        logEvent("📨 Caricate " + allEmails.size() + " email totali per: " + userEmail);
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

    public void stopServer() {
        if (!isRunning) return;

        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            executorService.shutdownNow();
            executorService = Executors.newCachedThreadPool();
            logEvent("✅ Server arrestato");
        } catch (IOException e) {
            logEvent("❌ Errore arresto server: " + e.getMessage());
        }
        Platform.runLater(() -> startStopButton.setText("Start Server"));
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
}