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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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

            // Ottieni il requesting user per i comandi che lo richiedono
            if (requiresRequestingUser(command)) {
                requestingUser = (String) NetworkUtils.receiveObject(clientSocket);
            }

            switch (command) {
                case "CHECK_COMPOSE":
                    NetworkUtils.sendObject(clientSocket, "OK");
                    logEvent("✅ Richiesta composizione nuova email");
                    break;
                case "GET_REPLY_TEMPLATE":
                    handleReplyTemplate(clientSocket, requestingUser);
                    break;
                case "GET_REPLY_ALL_TEMPLATE":
                    handleReplyAllTemplate(clientSocket, requestingUser);
                    break;
                case "GET_FORWARD_TEMPLATE":
                    handleForwardTemplate(clientSocket, requestingUser);
                    break;
                case "SEND_EMAIL":
                    handleSendEmail(clientSocket);
                    break;
                case "PREPARE_REPLY":
                    handlePrepareReply(clientSocket, requestingUser);
                    break;
                case "PREPARE_REPLY_ALL":
                    handlePrepareReplyAll(clientSocket, requestingUser);
                    break;
                case "PREPARE_FORWARD":
                    handlePrepareForward(clientSocket, requestingUser);
                    break;
                case "DELETE_EMAIL":
                    handleDeleteEmail(clientSocket, requestingUser);
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
        return command.equals("GET_REPLY_TEMPLATE") ||
                command.equals("GET_REPLY_ALL_TEMPLATE") ||
                command.equals("GET_FORWARD_TEMPLATE") ||
                command.equals("PREPARE_REPLY") ||
                command.equals("PREPARE_REPLY_ALL") ||
                command.equals("PREPARE_FORWARD") ||
                command.equals("DELETE_EMAIL");
    }

    private void handleReplyTemplate(Socket clientSocket, String requestingUser) throws IOException, ClassNotFoundException {
        int emailId = (int) NetworkUtils.receiveObject(clientSocket);

        Email originalEmail = mailServer.getEmailById(emailId, requestingUser);
        if (originalEmail == null) {
            sendError(clientSocket, "Email non trovata o accesso negato");
            return;
        }

        Email replyTemplate = createReplyTemplate(originalEmail);
        NetworkUtils.sendObject(clientSocket, "OK");
        NetworkUtils.sendObject(clientSocket, replyTemplate);
        logEvent("📧 Template risposta creato per email: " + emailId);
    }

    private void handleReplyAllTemplate(Socket clientSocket, String requestingUser) throws IOException, ClassNotFoundException {
        int emailId = (int) NetworkUtils.receiveObject(clientSocket);

        Email originalEmail = mailServer.getEmailById(emailId, requestingUser);
        if (originalEmail == null) {
            sendError(clientSocket, "Email non trovata o accesso negato");
            return;
        }

        Email replyAllTemplate = createReplyAllTemplate(originalEmail, requestingUser);
        NetworkUtils.sendObject(clientSocket, "OK");
        NetworkUtils.sendObject(clientSocket, replyAllTemplate);
        logEvent("📧 Template risposta a tutti creato per email: " + emailId);
    }

    private void handleForwardTemplate(Socket clientSocket, String requestingUser) throws IOException, ClassNotFoundException {
        int emailId = (int) NetworkUtils.receiveObject(clientSocket);

        Email originalEmail = mailServer.getEmailById(emailId, requestingUser);
        if (originalEmail == null) {
            sendError(clientSocket, "Email non trovata o accesso negato");
            return;
        }

        Email forwardTemplate = createForwardTemplate(originalEmail);
        NetworkUtils.sendObject(clientSocket, "OK");
        NetworkUtils.sendObject(clientSocket, forwardTemplate);
        logEvent("📧 Template inoltro creato per email: " + emailId);
    }

    private Email createReplyTemplate(Email originalEmail) {
        Email template = new Email();
        template.setRecipients(Collections.singletonList(originalEmail.getSender()));
        template.setSubject("Re: " + originalEmail.getSubject());
        template.setBody("\n\n----- Messaggio Originale -----\n" + originalEmail.getBody());
        return template;
    }

    private Email createReplyAllTemplate(Email originalEmail, String requestingUser) {
        Set<String> recipients = new HashSet<>(originalEmail.getRecipients());
        recipients.add(originalEmail.getSender());
        recipients.remove(requestingUser);

        Email template = new Email();
        template.setRecipients(new ArrayList<>(recipients));
        template.setSubject("Re: " + originalEmail.getSubject());
        template.setBody("\n\n----- Messaggio Originale -----\n" + originalEmail.getBody());
        return template;
    }

    private Email createForwardTemplate(Email originalEmail) {
        Email template = new Email();
        template.setSubject("Fwd: " + originalEmail.getSubject());
        String forwardedContent = String.format("""
            
            ----- Messaggio Inoltrato -----
            Da: %s
            A: %s
            Oggetto: %s
            
            %s""",
                originalEmail.getSender(),
                String.join(", ", originalEmail.getRecipients()),
                originalEmail.getSubject(),
                originalEmail.getBody());
        template.setBody(forwardedContent);
        return template;
    }

    private void handleSendEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        Email newEmail = (Email) NetworkUtils.receiveObject(clientSocket);
        String senderEmail = (String) NetworkUtils.receiveObject(clientSocket);

        try {
            mailServer.sendEmail(newEmail);
            NetworkUtils.sendObject(clientSocket, "OK");
            logEvent("✅ Email inviata da: " + senderEmail);
        } catch (Exception e) {
            sendError(clientSocket, "Errore nell'invio dell'email: " + e.getMessage());
            logEvent("❌ Invio fallito da: " + senderEmail);
        }
    }

    private void handlePrepareReply(Socket clientSocket, String requestingUser) throws IOException, ClassNotFoundException {
        int emailId = (int) NetworkUtils.receiveObject(clientSocket);
        Email email = mailServer.getEmailById(emailId, requestingUser);
        NetworkUtils.sendObject(clientSocket, email != null ? "OK" : "ERROR");
        logEvent(email != null ?
                "🔄 Preparazione risposta per email: " + emailId :
                "❌ Accesso negato alla preparazione risposta per email: " + emailId);
    }

    private void handlePrepareReplyAll(Socket clientSocket, String requestingUser) throws IOException, ClassNotFoundException {
        int emailId = (int) NetworkUtils.receiveObject(clientSocket);
        Email email = mailServer.getEmailById(emailId, requestingUser);
        NetworkUtils.sendObject(clientSocket, email != null ? "OK" : "ERROR");
        logEvent(email != null ?
                "🔄 Preparazione risposta a tutti per email: " + emailId :
                "❌ Accesso negato alla preparazione risposta a tutti per email: " + emailId);
    }

    private void handlePrepareForward(Socket clientSocket, String requestingUser) throws IOException, ClassNotFoundException {
        int emailId = (int) NetworkUtils.receiveObject(clientSocket);
        Email email = mailServer.getEmailById(emailId, requestingUser);
        NetworkUtils.sendObject(clientSocket, email != null ? "OK" : "ERROR");
        logEvent(email != null ?
                "↪ Preparazione inoltro per email: " + emailId :
                "❌ Accesso negato alla preparazione inoltro per email: " + emailId);
    }

    private void handleDeleteEmail(Socket clientSocket, String requestingUser) throws IOException, ClassNotFoundException {
        int emailId = (int) NetworkUtils.receiveObject(clientSocket);
        boolean deleted = mailServer.deleteEmail(emailId, requestingUser);
        NetworkUtils.sendObject(clientSocket, deleted ? "OK" : "ERROR");
        logEvent(deleted ?
                "🗑 Email " + emailId + " eliminata da: " + requestingUser :
                "❌ Eliminazione email " + emailId + " fallita per: " + requestingUser);
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