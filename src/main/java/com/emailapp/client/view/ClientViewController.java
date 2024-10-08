package com.emailapp.client.view;

import com.emailapp.client.controller.ClientController;
import com.emailapp.client.model.Email;
import com.emailapp.client.model.EmailDraft;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

public class ClientViewController {

    @FXML private Label emailAddressLabel;
    @FXML private Label connectionStatusLabel;
    @FXML private TableView<Email> emailTableView;
    @FXML private TableColumn<Email, String> senderColumn;
    @FXML private TableColumn<Email, String> subjectColumn;
    @FXML private TableColumn<Email, String> dateColumn;
    @FXML private ListView<EmailDraft> activeDraftsListView;

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
        activeDraftsListView.setItems(clientController.getActiveDrafts());

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
        EmailDraft draft = clientController.startNewDraft();
        openDraftEditor(draft);
    }

    private void openDraftEditor(EmailDraft draft) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/emailapp/client/DraftEditorView.fxml"));
            Parent root = loader.load();
            DraftEditorViewController controller = loader.getController();
            controller.setDraft(draft);
            controller.setClientController(clientController);

            Stage stage = new Stage();
            stage.setTitle("Compose Email");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            // Mostra un messaggio di errore all'utente
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to open draft editor");
            alert.setContentText("An error occurred while trying to open the draft editor: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleRefreshEmails() {
        clientController.fetchNewEmails();
    }

    @FXML
    private void handleRefreshDrafts() {
        clientController.fetchActiveDrafts();
    }

    @FXML
    private void handleReplyEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            EmailDraft draft = clientController.createReplyDraft(selectedEmail);
            openDraftEditor(draft);
        }
    }

    @FXML
    private void handleReplyAllEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            EmailDraft draft = clientController.createReplyAllDraft(selectedEmail);
            openDraftEditor(draft);
        }
    }

    @FXML
    private void handleForwardEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            EmailDraft draft = clientController.createForwardDraft(selectedEmail);
            openDraftEditor(draft);
        }
    }

    @FXML
    private void handleDeleteEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            clientController.deleteEmail(selectedEmail);
        }
    }
}