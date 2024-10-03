package com.emailapp.server.view;

import com.emailapp.server.controller.ServerController;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

public class ServerViewController {
    @FXML
    private TextArea logTextArea;

    private ServerController serverController;

    public void setServerController(ServerController serverController) {
        this.serverController = serverController;
    }

    public void addLogEntry(String entry) {
        logTextArea.appendText(entry + "\n");
    }
}