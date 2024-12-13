package com.emailapp.client.controller;

import com.emailapp.util.Email;
import com.emailapp.client.model.EmailUpdateListener;
import com.emailapp.client.model.MailClient;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ClientController implements EmailUpdateListener {
    private static final int POLLING_INTERVAL = 1000;

    @FXML
    private Label emailAddressLabel;
    @FXML
    private Label connectionStatusLabel;
    @FXML
    private TableView<Email> emailTableView;
    @FXML
    private TextField toField;
    @FXML
    private TextField subjectField;
    @FXML
    private TextArea bodyArea;
    @FXML
    private TextFlow emailDetailFlow;
    @FXML
    private VBox composeView;
    @FXML
    private StackPane detailOrComposeStack;
    @FXML
    private Button sendButton;
    @FXML
    private HBox actionButtons;
    @FXML
    private TextArea emailDetailTextArea;
    @FXML
    private ComboBox<String> emailFilterComboBox;

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
            mailClient.addEmailUpdateListener(this);
            startEmailFetcher();
            startPolling();
            initialLoadCompleted = true;
        }
    }

    public void setEmailAddress(String emailAddress) {
        if (emailAddress.contains(",")) {
            emailAddress = emailAddress.split(",")[0].trim();
        }
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
        emailFilterComboBox.getItems().addAll(
                MailClient.RECEIVED_EMAILS,
                MailClient.SENT_EMAILS
        );
        emailFilterComboBox.setValue(currentFilter);
        emailFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentFilter = newVal;
                try {
                    mailClient.filterEmails(newVal);
                } catch (Exception e) {
                    handleConnectionError();
                }
            }
        });
    }

    private void setupEmailTableView() {
        TableColumn<Email, String> senderColumn = new TableColumn<>("From");
        senderColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSender()));
        senderColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    Email email = getTableView().getItems().get(getIndex());
                    setText(item);
                }
            }
        });

        TableColumn<Email, String> subjectColumn = new TableColumn<>("Subject");
        subjectColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSubject()));
        subjectColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    Email email = getTableView().getItems().get(getIndex());
                    setText(item);
                }
            }
        });

        TableColumn<Email, String> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        ));
        dateColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    Email email = getTableView().getItems().get(getIndex());
                    setText(item);
                }
            }
        });

        emailTableView.getColumns().setAll(senderColumn, subjectColumn, dateColumn);
        emailTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                handleEmailSelection(newSelection);
            }
        });
    }

    private void handleEmailSelection(Email email) {
        try {
            Platform.runLater(() -> {
                emailTableView.refresh();

                    if (currentFilter.equals(MailClient.RECEIVED_EMAILS)) {
                        ObservableList<Email> currentList = mailClient.getMailbox().getReceivedEmails();
                        emailTableView.setItems(null);
                        emailTableView.setItems(currentList);
                    }
                });
            displayEmailDetails(email);
        } catch (Exception e) {
            showErrorAlert("Errore", "Impossibile aprire l'email");
        }
    }

    private void filterEmails(String filter) {
        try {
            mailClient.handleEmailFiltering(filter);
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
            showErrorAlert("Server non raggiungibile", "Impossibile inviare l'email: server non raggiungibile");
            return;
        }

        try {
            String[] recipients = toField.getText().split("\\s*,\\s*");
            String response = mailClient.handleEmailSend(recipients, subjectField.getText(), bodyArea.getText());

            if ("OK".equals(response)) {
                showEmailListView();
                showInfoAlert("Email Inviata", "Email inviata con successo");
                filterEmails(currentFilter);
            } else {
                showErrorAlert("Errore", "Impossibile inviare l'email: " + response);
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

        Platform.runLater(() -> {
            Email replyTemplate = mailClient.createReplyEmail(currentDisplayedEmail);
            populateComposeFields(replyTemplate);
            showComposeView();
        });
    }

    @FXML
    private void handleReplyAllEmail() {
        if (currentDisplayedEmail == null) {
            showErrorAlert("Nessuna Selezione", "Seleziona un'email per rispondere a tutti");
            return;
        }

        Platform.runLater(() -> {
            Email replyAllTemplate = mailClient.createReplyAllEmail(currentDisplayedEmail);
            populateComposeFields(replyAllTemplate);
            showComposeView();
        });
    }

    @FXML
    private void handleForwardEmail() {
        if (currentDisplayedEmail == null) {
            showErrorAlert("Nessuna Selezione", "Seleziona un'email da inoltrare");
            return;
        }

        Platform.runLater(() -> {
            Email forwardTemplate = mailClient.createForwardEmail(currentDisplayedEmail);
            populateComposeFields(forwardTemplate);
            showComposeView();
        });
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
                if (!mailClient.isConnected()) {
                    showErrorAlert("Server non raggiungibile", "Impossibile eliminare l'email: server non raggiungibile");
                    return;
                }

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
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(POLLING_INTERVAL), e -> {
            if (mailClient.isConnected()) {
                mailClient.pollForNewEmails();
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
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
            details.append("From: ").append(email.getSender()).append("\n");
            details.append("To: ").append(String.join(", ", email.getRecipients())).append("\n");
            details.append("Subject: ").append(email.getSubject()).append("\n");
            details.append("Date: ").append(email.getSentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append("\n");
            details.append("\n");
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
            try {
                mailClient.filterEmails(currentFilter);
            } catch (Exception e) {
                handleConnectionError();
            }
        });
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
        mailClient.removeEmailUpdateListener(this);
        Platform.runLater(() -> {
            if (emailTableView != null) {
                emailTableView.setItems(null);
            }
        });


        if (mailClient != null) {
            mailClient.shutdown();
        }


        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    @Override
    public void onNewEmailsReceived(List<Email> newEmails) {
        Platform.runLater(() -> {

            newEmails.sort((e1, e2) -> e2.getSentDate().compareTo(e1.getSentDate()));

            for (Email email : newEmails) {
                if (!mailClient.getMailbox().hasEmail(email.getId())) {
                    mailClient.getMailbox().addNewEmail(email);
                    ObservableList<Email> items = emailTableView.getItems();
                    FXCollections.sort(items,
                            (e1, e2) -> e2.getSentDate().compareTo(e1.getSentDate()));
                    emailTableView.refresh();
                }
            }
        });
    }


    @Override
    public void onEmailUpdateError(Exception e) {
        handleConnectionError();
    }

    @Override
    public void onEmailsFiltered(String filter, List<Email> emails) {
        Platform.runLater(() -> {
            if (emails != null) {
                if (filter.equals(MailClient.SENT_EMAILS)) {
                    updateEmailList(mailClient.getMailbox().getSentEmails(), emails);
                    emailTableView.setItems(mailClient.getMailbox().getSentEmails());
                } else {
                    updateEmailList(mailClient.getMailbox().getReceivedEmails(), emails);
                    emailTableView.setItems(mailClient.getMailbox().getReceivedEmails());
                }
                emailTableView.refresh();
            }
        });
    }

    private void updateEmailList(ObservableList<Email> currentList, List<Email> newEmails) {
        newEmails.sort((e1, e2) -> e2.getSentDate().compareTo(e1.getSentDate()));
        List<Email> emailsToAdd = newEmails.stream()
                .filter(newEmail -> !currentList.stream()
                        .anyMatch(e -> e.getId() == newEmail.getId()))
                .toList();
        Platform.runLater(() -> {
            currentList.addAll(0, emailsToAdd);
            FXCollections.sort(currentList,
                    (e1, e2) -> e2.getSentDate().compareTo(e1.getSentDate()));
        });
    }

}

