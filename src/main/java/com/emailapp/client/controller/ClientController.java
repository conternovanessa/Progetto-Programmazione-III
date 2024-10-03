package com.emailapp.client.controller;

import com.emailapp.shared.Email;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class ClientController {

    @FXML
    private TableView<Email> emailTableView;

    @FXML
    private TableColumn<Email, String> fromColumn;

    @FXML
    private TableColumn<Email, String> subjectColumn;

    @FXML
    private TableColumn<Email, String> dateColumn;

    @FXML
    private void composeEmail() {
        // Implementa la logica per comporre una nuova email
    }

    @FXML
    private void refreshInbox() {
        // Implementa la logica per aggiornare la casella di posta
    }

    // Inizializza la tabella e configura le colonne
    @FXML
    private void initialize() {
        // ...
    }
}
