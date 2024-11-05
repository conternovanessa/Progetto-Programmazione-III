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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerController {
    private final MailServer mailServer;
    private ExecutorService executorService;
    private ServerSocket serverSocket;
    private boolean isRunning;
    private static final int DEFAULT_PORT = 5000;

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

    @FXML
    private void handleStartStop() {
        if (isRunning) {
            handleStopServer();
        } else {
            int port = Integer.parseInt(portLabel.getText());
            startServer(port);
        }
        updateButtonState();
    }

    private void updateButtonState() {
        startStopButton.setText(isRunning ? "Stop Server" : "Start Server");
    }

    public void startServer(int port) {
        if (isRunning) {
            return;
        }
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            mailServer.loadExistingEmails();
            executorService.submit(this::acceptConnections);
            logEvent("Server avviato sulla porta " + port);
        } catch (IOException e) {
            logEvent("Impossibile avviare il server: " + e.getMessage());
        }
    }

    private void acceptConnections() {
        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (isRunning) {
                    logEvent("Errore durante l'accettazione della connessione client: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        ObjectInputStream inputStream = null;
        boolean isAbnormalDisconnection = false;

        try {
            inputStream = new ObjectInputStream(clientSocket.getInputStream());
            clientSocket.setSoTimeout(30000);

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
                            logEvent("Comando sconosciuto ricevuto: " + command);
                            break;
                    }
                } catch (SocketException se) {
                    if (se.getMessage().contains("Ripristino della connessione") ||
                            se.getMessage().contains("Socket chiusa") ||
                            se.getMessage().contains("Lettura scaduta")) {
                        break;
                    }
                    isAbnormalDisconnection = true;
                    logEvent("Errore socket imprevisto: " + se.getMessage());
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            if (isRunning && !(e instanceof EOFException)) {
                isAbnormalDisconnection = true;
                logEvent("Errore nella connessione del client: " + e.getMessage());
            }
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                    if (isAbnormalDisconnection) {
                        logEvent("Connessione client chiusa dopo un errore");
                    }
                }
            } catch (IOException e) {
                if (isRunning) {
                    logEvent("Errore durante la chiusura delle risorse client:" + e.getMessage());
                }
            }
        }
    }

    private void handleSendEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        try {
            Email email = (Email) NetworkUtils.receiveObject(clientSocket);
            mailServer.sendEmail(email);
        } finally {
            if (clientSocket != null && !clientSocket.isClosed()) {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    logEvent("Errore durante la chiusura del socket in handleSendEmail: " + e.getMessage());
                }
            }
        }
    }

    private void handleFetchNewEmails(Socket clientSocket) throws IOException, ClassNotFoundException {
        try {
            String recipient = (String) NetworkUtils.receiveObject(clientSocket);
            List<Email> newEmails = mailServer.getNewEmails(recipient);
            NetworkUtils.sendObject(clientSocket, newEmails);
        } finally {
            if (clientSocket != null && !clientSocket.isClosed()) {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    logEvent("Errore durante la chiusura del socket in handleFetchNewEmails: " + e.getMessage());
                }
            }
        }
    }

    private void handleDeleteEmail(Socket clientSocket) throws IOException, ClassNotFoundException {
        int emailId = (int) NetworkUtils.receiveObject(clientSocket);
        String requestingUser = (String) NetworkUtils.receiveObject(clientSocket);

        boolean deleted = mailServer.deleteEmail(emailId, requestingUser);
        NetworkUtils.sendObject(clientSocket, deleted ? "OK" : "ERROR");
        if (deleted) {
            logEvent("✅ Email eliminata con successo - ID: " + emailId + " | Utente: " + requestingUser);
        } else {
            logEvent("❌ Eliminazione email fallita - ID: " + emailId + " | Utente: " + requestingUser);
        }
    }

    @FXML
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

    public void stopServer() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logEvent("Errore durante la chiusura del socket del server: " + e.getMessage());
            }
        }
        executorService.shutdownNow();
        executorService = Executors.newCachedThreadPool();
        logEvent("Server stopped");
        Platform.runLater(() -> startStopButton.setText("Start Server"));
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