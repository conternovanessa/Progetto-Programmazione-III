package com.emailapp.server.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

public class ServerController {

    @FXML
    private TextArea logArea;

    @FXML
    private void startServer() {
        // Implementa la logica per avviare il server
        logArea.appendText("Server started...\n");
    }

    public void log(String message) {
        logArea.appendText(message + "\n");
    }
}
