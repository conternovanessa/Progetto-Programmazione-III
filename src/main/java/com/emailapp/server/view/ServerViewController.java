package com.emailapp.server.view;

import com.emailapp.server.controller.ServerController;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;

public class ServerViewController {
    @FXML
    private TextArea logTextArea;

    @FXML
    private Button toggleServerButton;

    private ServerController serverController;

    public void setServerController(ServerController serverController) {
        this.serverController = serverController;
    }

    public void addLogEntry(String entry) {
        if (logTextArea != null) {
            logTextArea.appendText(entry + "\n");
        } else {
            System.err.println("logTextArea is null. Cannot add log entry: " + entry);
        }
    }

    @FXML
    private void handleToggleServer() {
        if (serverController.isServerRunning()) {
            serverController.stopServer();
            toggleServerButton.setText("Start Server");
        } else {
            serverController.startServer();
            toggleServerButton.setText("Stop Server");
        }
    }

    public void updateServerStatus(boolean isRunning) {
        toggleServerButton.setText(isRunning ? "Stop Server" : "Start Server");
    }
}