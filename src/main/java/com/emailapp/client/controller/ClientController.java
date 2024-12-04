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

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientController {
    private static final int POLLING_INTERVAL = 1000;

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
    private final ExecutorService executorService;
    private String currentFilter = "Email ricevute";
    private Email currentDisplayedEmail;
    private boolean initialLoadCompleted = false;
    private volatile boolean alertShown = false;
    private boolean isComposeViewVisible = false;

    public ClientController() {
        this.mailClient = new MailClient("");
        this.executorService = Executors.newCachedThreadPool();
    }

    @FXML
    private void initialize() {
        if (!initialLoadCompleted) {
            setupConnectionListener();
            startConnectionChecker();
            setupEmailTableView();
            setupEmailFilter();
            startEmailFetcher();
            startPolling();
            initialLoadCompleted = true;
        }
    }

    public void setEmailAddress(String emailAddress) {
        mailClient.getMailbox().setEmailAddress(emailAddress);
        emailAddressLabel.setText(emailAddress);
    }

    private void setupConnectionListener() {
        mailClient.connectedProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> {
                connectionStatusLabel.setText(newValue ? "Connected" : "Disconnected");
                connectionStatusLabel.setStyle(newValue ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
            });
        });
    }

    private void setupEmailFilter() {
        emailFilterComboBox.getItems().addAll("Email ricevute", "Email inviate");
        emailFilterComboBox.setValue(currentFilter);
        emailFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentFilter = newVal;
                filterEmails(newVal);
            }
        });
    }

    private void setupEmailTableView() {
        TableColumn<Email, String> senderColumn = new TableColumn<>("From");
        senderColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSender()));

        TableColumn<Email, String> subjectColumn = new TableColumn<>("Subject");
        subjectColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSubject()));

        TableColumn<Email, String> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        ));

        emailTableView.getColumns().setAll(senderColumn, subjectColumn, dateColumn);
        emailTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                handleEmailSelection(newSelection);
            }
        });
    }

    private void handleEmailSelection(Email email) {
        if (!mailClient.isConnected()) {
            showErrorAlert("Server non raggiungibile", "Impossibile aprire l'email: server non disponibile");
            return;
        }

        try {
            mailClient.markEmailAsRead(email);
            displayEmailDetails(email);
        } catch (Exception e) {
            showErrorAlert("Errore", "Impossibile aprire l'email");
        }
    }

    private void filterEmails(String filter) {
        if (!mailClient.isConnected()) {
            showErrorAlert("Server Disconnesso", "Impossibile filtrare le email: server non raggiungibile");
            return;
        }

        try {
            List<Email> emails = mailClient.fetchEmails(filter);
            Platform.runLater(() -> {
                mailClient.getMailbox().clearEmails();
                if (emails != null) {
                    if (filter.equals("Email inviate")) {
                        emails.forEach(mailClient.getMailbox()::addSentEmail);
                        emailTableView.setItems(mailClient.getMailbox().getSentEmails());
                    } else {
                        emails.forEach(mailClient.getMailbox()::addReceivedEmail);
                        emailTableView.setItems(mailClient.getMailbox().getReceivedEmails());
                    }
                    emailTableView.refresh();
                }
            });
        } catch (Exception e) {
            handleConnectionError();
        }
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
            showErrorAlert("Server Disconnesso", "Impossibile inviare l'email: server non raggiungibile");
            return;
        }

        try {
            Email newEmail = createEmailFromFields();
            String response = mailClient.sendEmail(newEmail);

            if ("OK".equals(response)) {
                showEmailListView();
                showInfoAlert("Email Inviata", "Email inviata con successo");
                filterEmails(currentFilter);
            } else {
                String cleanedResponse = response.replace("ERROR: ", "");
                showErrorAlert("Errore", "Impossibile inviare l'email: " + cleanedResponse);
            }
        } catch (Exception e) {
            handleConnectionError();
        }
    }

    @FXML
    private void handleReplyEmail() {
        if (currentDisplayedEmail == null) {
            showErrorAlert("Nessuna Selezione", "Seleziona un'email per rispondere");
            return;
        }

        try {
            Email replyTemplate = mailClient.createReplyEmail("REPLY",currentDisplayedEmail.getId());
            populateComposeFields(replyTemplate);
            showComposeView();
        } catch (Exception e) {
            showErrorAlert("Errore di Connessione", "Impossibile connettersi al server");
        }
    }

    @FXML
    private void handleReplyAllEmail() {
        if (currentDisplayedEmail == null) {
            showErrorAlert("Nessuna Selezione", "Seleziona un'email per rispondere a tutti");
            return;
        }

        try {
            Email replyAllTemplate = mailClient.createReplyEmail("REPLY_ALL",currentDisplayedEmail.getId());
            populateComposeFields(replyAllTemplate);
            showComposeView();
        } catch (Exception e) {
            showErrorAlert("Errore di Connessione", "Impossibile connettersi al server");
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
            showErrorAlert("Errore di Connessione", "Impossibile connettersi al server");
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
                    mailClient.deleteEmail(String.valueOf(currentDisplayedEmail.getId()));
                    mailClient.getMailbox().removeEmail(currentDisplayedEmail);
                    showEmailListView();
                    showInfoAlert("Email Eliminata", "Email eliminata con successo");
                } catch (Exception e) {
                    handleConnectionError();
                }
            }
        }
    }

    private void startEmailFetcher() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (mailClient.isConnected()) {
                        filterEmails(currentFilter);
                    }
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void startPolling() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(POLLING_INTERVAL), e -> pollForNewEmails()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void pollForNewEmails() {
        try {
            List<Email> newEmails = mailClient.checkNewEmails();
            if (newEmails != null && !newEmails.isEmpty()) {
                Platform.runLater(() -> {
                    boolean wasDetailViewVisible = emailDetailTextArea.isVisible();
                    boolean wasComposeViewVisible = composeView.isVisible();
                    Email selectedEmail = currentDisplayedEmail;

                    if (currentFilter.equals("Email ricevute")) {
                        newEmails.forEach(mailClient.getMailbox()::addReceivedEmail);
                        emailTableView.setItems(mailClient.getMailbox().getReceivedEmails());
                        emailTableView.refresh();
                    }

                    if (wasDetailViewVisible && selectedEmail != null) {
                        displayEmailDetails(selectedEmail);
                    } else if (wasComposeViewVisible) {
                        showComposeView();
                    }
                });
            }
        } catch (Exception e) {
            handleConnectionError();
        }
    }

    private void startConnectionChecker() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                mailClient.checkConnection();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void displayEmailDetails(Email email) {
        Platform.runLater(() -> {
            currentDisplayedEmail = email;
            StringBuilder details = new StringBuilder();
            details.append("Da: ").append(email.getSender()).append("\n");
            details.append("A: ").append(String.join(", ", email.getRecipients())).append("\n");
            details.append("Oggetto: ").append(email.getSubject()).append("\n\n");
            details.append(email.getBody());

            emailDetailTextArea.setText(details.toString());
            emailDetailTextArea.setVisible(true);
            emailDetailFlow.setVisible(true);
            emailTableView.setVisible(false);
            actionButtons.setVisible(true);
            composeView.setVisible(false);

            if (!detailOrComposeStack.getChildren().contains(emailDetailTextArea)) {
                detailOrComposeStack.getChildren().clear();
                detailOrComposeStack.getChildren().add(emailDetailTextArea);
            }
        });
    }

    @FXML
    private void handleBackButton() {
        Platform.runLater(() -> {
            emailTableView.setVisible(true);
            emailDetailFlow.setVisible(false);
            emailDetailTextArea.setVisible(false);
            actionButtons.setVisible(false);
            composeView.setVisible(false);

            emailTableView.getSelectionModel().clearSelection();
            refreshEmailList();
        });
    }

    private void showComposeView() {
        isComposeViewVisible = true;
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
            isComposeViewVisible = false;
            composeView.setVisible(false);
            emailDetailFlow.setVisible(false);
            emailDetailTextArea.setVisible(false);
            emailTableView.setVisible(true);
            actionButtons.setVisible(false);

            emailTableView.getSelectionModel().clearSelection();
            refreshEmailList();
        });
    }

    private void refreshEmailList() {
        Platform.runLater(() -> {
            filterEmails(currentFilter);
            emailTableView.refresh();
        });
    }

    private Email createEmailFromFields() {
        Email email = new Email();
        email.setRecipients(Arrays.asList(toField.getText().split("\\s*,\\s*")));
        email.setSubject(subjectField.getText());
        email.setBody(bodyArea.getText());
        email.setSender(mailClient.getMailbox().getEmailAddress());
        return email;
    }

    private void populateComposeFields(Email email) {
        toField.clear();
        subjectField.setText(email.getSubject() != null ? email.getSubject() : "");

        if (email.getRecipients() != null && !email.getRecipients().isEmpty()) {
            toField.setText(String.join(", ", email.getRecipients()));
        }

        bodyArea.setText(email.getBody() != null ? email.getBody() : "");
    }

    private void clearComposeFields() {
        toField.clear();
        subjectField.clear();
        bodyArea.clear();
    }

    private void handleConnectionError() {
        if (!alertShown) {
            Platform.runLater(() -> {
                showErrorAlert("Errore di Connessione", "Impossibile connettersi al server");
                alertShown = true;
            });
        }
    }

    private void showErrorAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.initModality(Modality.NONE);
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

    public void shutdown() {
        executorService.shutdown();
    }
}