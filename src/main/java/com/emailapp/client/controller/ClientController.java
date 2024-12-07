package com.emailapp.client.controller;

import com.emailapp.client.model.Email;
import com.emailapp.client.model.MailClient;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.util.Duration;
import javafx.beans.property.SimpleStringProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientController implements MailClient.UICallback {
    @FXML private Label emailAddressLabel;
    @FXML private Label connectionStatusLabel;
    @FXML private TableView<Email> emailTableView;
    @FXML private TextField toField;
    @FXML private TextField subjectField;
    @FXML private TextArea bodyArea;
    @FXML private TextFlow emailDetailFlow;
    @FXML private VBox composeView;
    @FXML private StackPane detailOrComposeStack;
    @FXML private Button sendButton;
    @FXML private HBox actionButtons;
    @FXML private TextArea emailDetailTextArea;
    @FXML private ComboBox<String> emailFilterComboBox;

    private final MailClient mailClient;
    private Email currentDisplayedEmail;
    private boolean initialLoadCompleted = false;

    public ClientController() {
        this.mailClient = new MailClient("", this);
    }

    @FXML
    private void initialize() {
        if (!initialLoadCompleted) {
            setupEmailTableView();
            setupEmailFilter();
            mailClient.startConnectionChecker();
            mailClient.startEmailFetcher();
            initialLoadCompleted = true;
        }
    }

    private void setupEmailTableView() {
        TableColumn<Email, String> senderColumn = new TableColumn<>("From");
        senderColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getSender()));

        TableColumn<Email, String> subjectColumn = new TableColumn<>("Subject");
        subjectColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getSubject()));

        TableColumn<Email, String> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getSentDate()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));

        emailTableView.getColumns().setAll(senderColumn, subjectColumn, dateColumn);
        emailTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        handleEmailSelection(newSelection);
                    }
                });
    }

    private void setupEmailFilter() {
        emailFilterComboBox.getItems().addAll("Email ricevute", "Email inviate");
        emailFilterComboBox.setValue("Email ricevute");
        emailFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                mailClient.setCurrentFilter(newVal);
            }
        });
    }

    @FXML
    private void handleComposeEmail() {
        Platform.runLater(() -> {
            clearComposeFields();
            showComposeView();
        });
    }

    @FXML
    private void handleSendEmail() {
        if (!mailClient.isConnected()) {
            showErrorAlert("Server Disconnesso", "Impossibile inviare l'email");
            return;
        }

        try {
            Email email = createEmailFromFields();
            String response = mailClient.sendEmail(email);
            if ("OK".equals(response)) {
                showEmailListView();
                showInfoAlert("Email Inviata", "Email inviata con successo");
            }
        } catch (Exception e) {
            showErrorAlert("Errore", "Impossibile inviare l'email");
        }
    }

    @FXML
    private void handleReplyEmail() {
        if (currentDisplayedEmail == null) {
            showErrorAlert("Nessuna Selezione", "Seleziona un'email per rispondere");
            return;
        }

        try {
            Email replyTemplate = mailClient.createReplyEmail("REPLY", currentDisplayedEmail.getId());
            populateComposeFields(replyTemplate);
            showComposeView();
        } catch (Exception e) {
            showErrorAlert("Errore", "Impossibile creare risposta");
        }
    }

    @FXML
    private void handleReplyAllEmail() {
        if (currentDisplayedEmail == null) {
            showErrorAlert("Nessuna Selezione", "Seleziona un'email per rispondere a tutti");
            return;
        }

        try {
            Email replyTemplate = mailClient.createReplyEmail("REPLY_ALL", currentDisplayedEmail.getId());
            populateComposeFields(replyTemplate);
            showComposeView();
        } catch (Exception e) {
            showErrorAlert("Errore", "Impossibile creare risposta a tutti");
        }
    }

    @FXML
    private void handleForwardEmail() {
        if (currentDisplayedEmail == null) {
            showErrorAlert("Nessuna Selezione", "Seleziona un'email da inoltrare");
            return;
        }

        try {
            Email forwardTemplate = mailClient.createReplyEmail("FORWARD", currentDisplayedEmail.getId());
            populateComposeFields(forwardTemplate);
            showComposeView();
        } catch (Exception e) {
            showErrorAlert("Errore", "Impossibile inoltrare email");
        }
    }

    @FXML
    private void handleDeleteEmail() {
        if (currentDisplayedEmail != null) {
            Alert confirmDelete = new Alert(Alert.AlertType.CONFIRMATION);
            confirmDelete.setTitle("Conferma eliminazione");
            confirmDelete.setHeaderText("Eliminare questa email?");
            confirmDelete.setContentText("Questa operazione non può essere annullata.");

            Optional<ButtonType> result = confirmDelete.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    // Delete from server
                    mailClient.deleteEmail(String.valueOf(currentDisplayedEmail.getId()));

                    // Immediately update local view
                    Platform.runLater(() -> {
                        // Remove from mailbox
                        mailClient.getMailbox().removeEmail(currentDisplayedEmail);

                        // Update TableView based on current filter
                        if (emailFilterComboBox.getValue().equals("Email inviate")) {
                            emailTableView.setItems(mailClient.getMailbox().getSentEmails());
                        } else {
                            emailTableView.setItems(mailClient.getMailbox().getReceivedEmails());
                        }

                        // Refresh view and show success message
                        emailTableView.refresh();
                        showEmailListView();
                        showInfoAlert("Email Eliminata", "Email eliminata con successo");
                    });

                } catch (Exception e) {
                    showErrorAlert("Errore", "Impossibile eliminare l'email");
                }
            }
        }
    }


    @FXML
    private void handleBackButton() {
        showEmailListView();
    }

    // UICallback implementations
    @Override
    public void updateEmailList(List<Email> emails) {
        Platform.runLater(() -> {
            mailClient.getMailbox().clearEmails();
            if (emails != null) {
                if (emailFilterComboBox.getValue().equals("Email inviate")) {
                    emails.forEach(mailClient.getMailbox()::addSentEmail);
                    emailTableView.setItems(mailClient.getMailbox().getSentEmails());
                } else {
                    emails.forEach(mailClient.getMailbox()::addReceivedEmail);
                    emailTableView.setItems(mailClient.getMailbox().getReceivedEmails());
                }
                emailTableView.refresh();
            }
        });
    }

    @Override
    public void handleError(String message) {
        Platform.runLater(() -> showErrorAlert("Errore", message));
    }

    @Override
    public void handleNewEmails(List<Email> emails, String filter) {
        Platform.runLater(() -> {
            if (filter.equals("Email ricevute")) {
                emails.forEach(mailClient.getMailbox()::addReceivedEmail);
                emailTableView.setItems(mailClient.getMailbox().getReceivedEmails());
                emailTableView.refresh();
            }
        });
    }

    @Override
    public void updateConnectionStatus(boolean connected) {
        Platform.runLater(() -> {
            connectionStatusLabel.setText(connected ? "Connected" : "Disconnected");
            connectionStatusLabel.setStyle(connected ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
        });
    }

    // Helper methods
    private void handleEmailSelection(Email email) {
        try {
            mailClient.markEmailAsRead(email);
            displayEmailDetails(email);
        } catch (Exception e) {
            showErrorAlert("Errore", "Impossibile aprire l'email");
        }
    }

    private void displayEmailDetails(Email email) {
        Platform.runLater(() -> {
            currentDisplayedEmail = email;
            StringBuilder details = new StringBuilder()
                    .append("Da: ").append(email.getSender()).append("\n")
                    .append("A: ").append(String.join(", ", email.getRecipients())).append("\n")
                    .append("Oggetto: ").append(email.getSubject()).append("\n\n")
                    .append(email.getBody());

            emailDetailTextArea.setText(details.toString());
            showDetailView();
        });
    }

    private void showDetailView() {
        emailDetailTextArea.setVisible(true);
        emailDetailFlow.setVisible(true);
        emailTableView.setVisible(false);
        actionButtons.setVisible(true);
        composeView.setVisible(false);

        if (!detailOrComposeStack.getChildren().contains(emailDetailTextArea)) {
            detailOrComposeStack.getChildren().clear();
            detailOrComposeStack.getChildren().add(emailDetailTextArea);
        }
    }

    private void showComposeView() {
        composeView.setVisible(true);
        emailDetailFlow.setVisible(false);
        emailTableView.setVisible(false);
        emailDetailTextArea.setVisible(false);
        actionButtons.setVisible(false);
        detailOrComposeStack.getChildren().clear();
        detailOrComposeStack.getChildren().add(composeView);
        sendButton.setVisible(true);
    }

    private void showEmailListView() {
        Platform.runLater(() -> {
            composeView.setVisible(false);
            emailDetailFlow.setVisible(false);
            emailDetailTextArea.setVisible(false);
            emailTableView.setVisible(true);
            actionButtons.setVisible(false);
            emailTableView.getSelectionModel().clearSelection();
        });
    }

    private Email createEmailFromFields() {
        Email email = new Email();
        email.setSender(mailClient.getMailbox().getEmailAddress());
        email.setRecipients(Arrays.asList(toField.getText().split("\\s*,\\s*")));
        email.setSubject(subjectField.getText().trim());
        email.setBody(bodyArea.getText().trim());
        email.setSentDate(LocalDateTime.now());
        return email;
    }

    private void populateComposeFields(Email email) {
        toField.setText(email.getRecipients() != null ?
                String.join(", ", email.getRecipients()) : "");
        subjectField.setText(email.getSubject() != null ? email.getSubject() : "");
        bodyArea.setText(email.getBody() != null ? email.getBody() : "");
    }

    private void clearComposeFields() {
        toField.clear();
        subjectField.clear();
        bodyArea.clear();
    }

    private void showErrorAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.show();
        });
    }

    private void showInfoAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    public void setEmailAddress(String emailAddress) {
        if (emailAddress.contains(",")) {
            emailAddress = emailAddress.split(",")[0].trim();
        }
        mailClient.getMailbox().setEmailAddress(emailAddress);
        emailAddressLabel.setText(emailAddress);
    }
}
