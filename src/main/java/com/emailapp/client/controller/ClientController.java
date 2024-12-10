package com.emailapp.client.controller;

import com.emailapp.util.Email;
import com.emailapp.client.model.EmailUpdateListener;
import com.emailapp.client.model.MailClient;
import com.emailapp.util.EmailFileManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
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
import java.util.stream.Collectors;

public class ClientController implements EmailUpdateListener {
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
            mailClient.addEmailUpdateListener(this);
            startEmailFetcher();
            startPolling();
            initialLoadCompleted = true;
        }
    }

    public void setEmailAddress(String emailAddress) {
        // Rimuovi la porta se presente nell'indirizzo email
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
        senderColumn.setCellFactory(tc -> new EmailTableCell());

        TableColumn<Email, String> subjectColumn = new TableColumn<>("Subject");
        subjectColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSubject()));
        subjectColumn.setCellFactory(tc -> new EmailTableCell());

        TableColumn<Email, String> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        ));
        dateColumn.setCellFactory(tc -> new EmailTableCell());

        emailTableView.getColumns().setAll(senderColumn, subjectColumn, dateColumn);

        // Add back the selection handler
        emailTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                handleEmailSelection(newSelection);
            }
        });

        emailTableView.setRowFactory(tv -> new TableRow<Email>() {
            @Override
            protected void updateItem(Email email, boolean empty) {
                super.updateItem(email, empty);
                if (email != null) {
                    setStyle(email.isRead() || currentFilter.equals(MailClient.SENT_EMAILS) ?
                            "-fx-font-weight: normal;" :
                            "-fx-font-weight: bold;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private class EmailTableCell extends TableCell<Email, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle("");
            } else {
                setText(item);
                Email email = getTableView().getItems().get(getIndex());

                // Usa direttamente isRead() che gestisce la proprietà JavaFX
                boolean isReadEmail = email.isRead();
                setStyle(!isReadEmail && currentFilter.equals(MailClient.RECEIVED_EMAILS) ?
                        "-fx-font-weight: bold;" :
                        "-fx-font-weight: normal;");
            }
        }
    }


    private <T> TableCell<Email, T> createStyledTableCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                Email email = getTableView().getItems().get(getIndex());
                setText(item.toString());

                // Only show bold if email is unread (isRead = false) and it's in received emails
                if (!email.isRead() && currentFilter.equals(MailClient.RECEIVED_EMAILS)) {
                    setStyle("-fx-font-weight: bold;");
                } else {
                    setStyle("-fx-font-weight: normal;");
                }
            }
        };
    }

    private void handleEmailSelection(Email email) {
        if (email == null) return;

        try {
            if (!email.isRead()) {
                mailClient.handleEmailRead(email);
                email.setRead(true);

                Platform.runLater(() -> {
                    // Aggiorna lo stato nel mailbox
                    mailClient.getMailbox().getReceivedEmails().stream()
                            .filter(e -> e.getId() == email.getId())
                            .forEach(e -> e.setRead(true));

                    // Forza il refresh della TableView
                    emailTableView.refresh();
                });
            }

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

        // Validazione campi
        if (toField.getText().trim().isEmpty()) {
            showErrorAlert("Errore", "Specificare almeno un destinatario");
            return;
        }

        if (subjectField.getText().trim().isEmpty()) {
            showErrorAlert("Errore", "Specificare l'oggetto dell'email");
            return;
        }

        try {
            String[] recipients = toField.getText().split("\\s*,\\s*");
            // Validazione formato email destinatari
            for (String recipient : recipients) {
                if (!recipient.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                    showErrorAlert("Errore", "Formato email non valido: " + recipient);
                    return;
                }
            }

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
            e.printStackTrace(); // Per debug
        }
    }





    @FXML
    private void handleReplyEmail() {
        if (currentDisplayedEmail == null) {
            showErrorAlert("Nessuna Selezione", "Seleziona un'email per rispondere");
            return;
        }

        Platform.runLater(() -> {
            try {
                if (mailClient.isConnected()) {
                    Email replyTemplate = mailClient.createActionEmail("REPLY", currentDisplayedEmail.getId());
                    populateComposeFields(replyTemplate);
                } else {
                    // Crea un template locale per la risposta
                    Email replyTemplate = new Email();
                    replyTemplate.setRecipients(Arrays.asList(currentDisplayedEmail.getSender()));
                    replyTemplate.setSubject("Re: " + currentDisplayedEmail.getSubject());
                    replyTemplate.setBody("\n\n----- Messaggio Originale -----\n" + currentDisplayedEmail.getBody());
                    populateComposeFields(replyTemplate);
                }
                showComposeView();
            } catch (Exception e) {
                // Gestione fallback locale in caso di errore
                Email replyTemplate = new Email();
                replyTemplate.setRecipients(Arrays.asList(currentDisplayedEmail.getSender()));
                replyTemplate.setSubject("Re: " + currentDisplayedEmail.getSubject());
                replyTemplate.setBody("\n\n----- Messaggio Originale -----\n" + currentDisplayedEmail.getBody());
                populateComposeFields(replyTemplate);
                showComposeView();
            }
        });
    }

    @FXML
    private void handleReplyAllEmail() {
        if (currentDisplayedEmail == null) {
            showErrorAlert("Nessuna Selezione", "Seleziona un'email per rispondere a tutti");
            return;
        }

        Platform.runLater(() -> {
            try {
                if (mailClient.isConnected()) {
                    Email replyAllTemplate = mailClient.createActionEmail("REPLY_ALL", currentDisplayedEmail.getId());
                    populateComposeFields(replyAllTemplate);
                } else {
                    // Crea un template locale per la risposta a tutti
                    Set<String> recipients = new HashSet<>(currentDisplayedEmail.getRecipients());
                    recipients.add(currentDisplayedEmail.getSender());
                    recipients.remove(mailClient.getMailbox().getEmailAddress());

                    Email replyAllTemplate = new Email();
                    replyAllTemplate.setRecipients(new ArrayList<>(recipients));
                    replyAllTemplate.setSubject("Re: " + currentDisplayedEmail.getSubject());
                    replyAllTemplate.setBody("\n\n----- Messaggio Originale -----\n" + currentDisplayedEmail.getBody());
                    populateComposeFields(replyAllTemplate);
                }
                showComposeView();
            } catch (Exception e) {
                // Gestione fallback locale in caso di errore
                Set<String> recipients = new HashSet<>(currentDisplayedEmail.getRecipients());
                recipients.add(currentDisplayedEmail.getSender());
                recipients.remove(mailClient.getMailbox().getEmailAddress());

                Email replyAllTemplate = new Email();
                replyAllTemplate.setRecipients(new ArrayList<>(recipients));
                replyAllTemplate.setSubject("Re: " + currentDisplayedEmail.getSubject());
                replyAllTemplate.setBody("\n\n----- Messaggio Originale -----\n" + currentDisplayedEmail.getBody());
                populateComposeFields(replyAllTemplate);
                showComposeView();
            }
        });
    }

    @FXML
    private void handleForwardEmail() {
        if (currentDisplayedEmail == null) {
            showErrorAlert("Nessuna Selezione", "Seleziona un'email da inoltrare");
            return;
        }

        Platform.runLater(() -> {
            try {
                if (mailClient.isConnected()) {
                    Email forwardTemplate = mailClient.createActionEmail("FORWARD", currentDisplayedEmail.getId());
                    populateComposeFields(forwardTemplate);
                } else {
                    // Crea un template locale per l'inoltro
                    Email forwardTemplate = new Email();
                    forwardTemplate.setSubject("Fwd: " + currentDisplayedEmail.getSubject());
                    forwardTemplate.setBody("\n\n----- Messaggio Inoltrato -----\n" +
                            "Da: " + currentDisplayedEmail.getSender() + "\n" +
                            "A: " + String.join(", ", currentDisplayedEmail.getRecipients()) + "\n" +
                            "Oggetto: " + currentDisplayedEmail.getSubject() + "\n\n" +
                            currentDisplayedEmail.getBody());
                    populateComposeFields(forwardTemplate);
                }
                showComposeView();
            } catch (Exception e) {
                // Gestione fallback locale in caso di errore
                Email forwardTemplate = new Email();
                forwardTemplate.setSubject("Fwd: " + currentDisplayedEmail.getSubject());
                forwardTemplate.setBody("\n\n----- Messaggio Inoltrato -----\n" +
                        "Da: " + currentDisplayedEmail.getSender() + "\n" +
                        "A: " + String.join(", ", currentDisplayedEmail.getRecipients()) + "\n" +
                        "Oggetto: " + currentDisplayedEmail.getSubject() + "\n\n" +
                        currentDisplayedEmail.getBody());
                populateComposeFields(forwardTemplate);
                showComposeView();
            }
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
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(POLLING_INTERVAL), e -> pollForNewEmails()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void pollForNewEmails() {
        try {
            List<Email> newEmails = mailClient.checkNewEmails();
            if (newEmails != null && !newEmails.isEmpty()) {
                Platform.runLater(() -> {
                    // Mantieni lo stato delle email esistenti
                    for (Email newEmail : newEmails) {
                        mailClient.getMailbox().getReceivedEmails().stream()
                                .filter(existingEmail -> existingEmail.getId() == newEmail.getId())
                                .findFirst()
                                .ifPresent(existingEmail -> {
                                    if (existingEmail.isRead()) {
                                        newEmail.setRead(true);
                                    }
                                });
                    }

                    if (currentFilter.equals("Email ricevute")) {
                        mailClient.getMailbox().getReceivedEmails().clear();
                        newEmails.forEach(mailClient.getMailbox()::addReceivedEmail);
                        emailTableView.refresh();
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

            if (currentDisplayedEmail != null) {
                // Aggiorna lo stato in tutti i posti necessari
                currentDisplayedEmail.setRead(true);

                // Aggiorna nella lista delle email ricevute
                mailClient.getMailbox().getReceivedEmails().stream()
                        .filter(e -> e.getId() == currentDisplayedEmail.getId())
                        .forEach(e -> e.setRead(true));

                // Aggiorna nella TableView corrente
                emailTableView.getItems().stream()
                        .filter(e -> e.getId() == currentDisplayedEmail.getId())
                        .forEach(e -> e.setRead(true));

                // Forza il refresh della TableView
                emailTableView.refresh();
            }

            emailTableView.getSelectionModel().clearSelection();
            currentDisplayedEmail = null;
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
        executorService.shutdown();
    }

    @Override
    public void onNewEmailsReceived(List<Email> newEmails) {
        Platform.runLater(() -> {
            if (currentFilter.equals("Email ricevute")) {
                // Create a map of existing emails
                Map<Integer, Email> existingEmails = new HashMap<>();
                mailClient.getMailbox().getReceivedEmails().forEach(email ->
                        existingEmails.put(email.getId(), email));

                // Update existing emails or add new ones
                List<Email> updatedEmails = new ArrayList<>();
                for (Email newEmail : newEmails) {
                    Email existingEmail = existingEmails.get(newEmail.getId());
                    if (existingEmail != null) {
                        // Preserve read status from existing email
                        newEmail.setRead(existingEmail.isRead());
                        updatedEmails.add(newEmail);
                    } else {
                        updatedEmails.add(newEmail);
                    }
                }

                // Update the mailbox while preserving read status
                mailClient.getMailbox().getReceivedEmails().setAll(updatedEmails);
                emailTableView.refresh();
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
            mailClient.getMailbox().clearEmails();
            if (emails != null) {
                if (filter.equals(MailClient.SENT_EMAILS)) {
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
    public void onEmailMarkedAsRead(Email email) {
        Platform.runLater(() -> {
            // Update mailbox and force table refresh
            mailClient.getMailbox().updateEmailReadStatus(email);
            ObservableList<Email> currentList = currentFilter.equals(MailClient.RECEIVED_EMAILS) ?
                    mailClient.getMailbox().getReceivedEmails() :
                    mailClient.getMailbox().getSentEmails();
            emailTableView.setItems(null);
            emailTableView.setItems(currentList);
            emailTableView.refresh();
        });
    }

    private void refreshTableRow(Email email) {
        Platform.runLater(() -> {
            int index = emailTableView.getItems().indexOf(email);
            if (index >= 0) {
                emailTableView.getItems().set(index, email);
                emailTableView.refresh();
            }
        });
    }



}