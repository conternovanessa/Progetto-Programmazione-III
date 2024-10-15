package com.emailapp.server;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

public class ServerViewController {
    @FXML private Label portLabel;
    @FXML private Button startStopButton;
    @FXML private TextArea logTextArea;

    private ServerController serverController;

    public void setServerController(ServerController serverController) {
        this.serverController = serverController;
        updateButtonState();
    }

    @FXML
    private void initialize() {
        portLabel.setText("5000");
    }

    @FXML
    private void handleStartStop() {
        if (serverController.isRunning()) {
            serverController.handleStopServer(); // Usa il nuovo metodo handleStopServer
        } else {
            int port = Integer.parseInt(portLabel.getText());
            serverController.startServer(port);
        }
        updateButtonState();
    }

    public void logEvent(String message) {
        logTextArea.appendText(message + "\n");
    }

    private void updateButtonState() {
        if (serverController.isRunning()) {
            startStopButton.setText("Stop Server");
        } else {
            startStopButton.setText("Start Server");
        }
    }

    // Aggiungi questo nuovo metodo
    public void onServerStopped() {
        startStopButton.setText("Start Server");
        // Altre modifiche all'interfaccia utente se necessarie
    }
}