package com.emailapp.client.view;

import com.emailapp.client.controller.ClientController;
import com.emailapp.client.model.Email;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;

public class ClientViewController {

    @FXML private Label emailAddressLabel;
    @FXML private Label connectionStatusLabel;
    @FXML private TableView<Email> emailTableView;
    @FXML private TableColumn<Email, String> senderColumn;
    @FXML private TableColumn<Email, String> subjectColumn;
    @FXML private TableColumn<Email, String> dateColumn;

    private ClientController clientController;
    private Stage primaryStage;

    public void initialize() {
        senderColumn.setCellValueFactory(new PropertyValueFactory<>("sender"));
        subjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        dateColumn.setCellValueFactory(cellData -> {
            Email email = cellData.getValue();
            String formattedDate = email.getSentDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            return javafx.beans.binding.Bindings.createStringBinding(() -> formattedDate);
        });

        emailTableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    public void setClientController(ClientController clientController) {
        this.clientController = clientController;
        emailAddressLabel.setText(clientController.getEmailAddress());
        emailTableView.setItems(clientController.getEmails());

        clientController.connectedProperty().addListener((observable, oldValue, newValue) -> {
            connectionStatusLabel.setText(newValue ? "Connected" : "Disconnected");
            connectionStatusLabel.setStyle(newValue ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
        });
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @FXML
    private void handleComposeEmail() {
        // Implementa la logica per aprire una nuova finestra di composizione email
    }

    @FXML
    private void handleRefreshEmails() {
        clientController.fetchNewEmails();
    }

    @FXML
    private void handleReplyEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            // Implementa la logica per rispondere all'email selezionata
        }
    }

    @FXML
    private void handleReplyAllEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            // Implementa la logica per rispondere a tutti per l'email selezionata
        }
    }

    @FXML
    private void handleForwardEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            // Implementa la logica per inoltrare l'email selezionata
        }
    }

    @FXML
    private void handleDeleteEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            clientController.deleteEmail(selectedEmail);
        }
    }

    // Metodi aggiuntivi per gestire la visualizzazione dei dettagli dell'email, ecc.
}