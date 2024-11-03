package com.emailapp.client;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.*;

public class RecentClientsWindow {
    private Stage stage;
    private final ListView<String> clientsList;
    private final ClientManager clientManager;

    public RecentClientsWindow(ClientManager clientManager) {
        this.clientManager = clientManager;
        this.clientsList = new ListView<>();
        initialize();
    }

    private void initialize() {
        stage = new Stage();
        stage.setTitle("Client recenti: ");

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        Label titleLabel = new Label(" ");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Button refreshButton = new Button("Aggiorna la lista");
        refreshButton.setOnAction(e -> updateClientsList());

        Button openSelectedButton = new Button("Apri il client selezionato");
        openSelectedButton.setOnAction(e -> {
            String selectedEmail = clientsList.getSelectionModel().getSelectedItem();
            if (selectedEmail != null) {
                clientManager.reopenClient(selectedEmail);
            }
        });

        Button clearHistoryButton = new Button("pulisci storia");
        clearHistoryButton.setOnAction(e -> {
            clientManager.clearHistory();
            updateClientsList();
        });

        root.getChildren().addAll(titleLabel, clientsList, refreshButton, openSelectedButton, clearHistoryButton);

        Scene scene = new Scene(root, 300, 400);
        stage.setScene(scene);

        stage.setOnShowing(e -> updateClientsList());
    }

    public void show() {
        if (!stage.isShowing()) {
            updateClientsList();
            stage.show();
        } else {
            stage.toFront();
        }
    }

    private void updateClientsList() {
        clientsList.getItems().clear();
        Set<String> allEmails = clientManager.getRegisteredEmails();
        for (String email : allEmails) {
            if (!clientManager.isClientActive(email)) {
                clientsList.getItems().add(email);
            }
        }
    }
}