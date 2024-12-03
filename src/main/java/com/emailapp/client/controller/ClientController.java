package com.emailapp.client.controller;

import com.emailapp.util.NetworkUtils;
import com.emailapp.client.model.Email;
import com.emailapp.client.model.Mailbox;
import javafx.animation.KeyFrame;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.animation.Timeline;
import javafx.stage.Modality;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ClientController {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

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

    private final Mailbox mailbox;
    private final ExecutorService executorService;
    private final BooleanProperty connectedProperty;
    private boolean isComposeViewVisible = false;
    private static final int POLLING_INTERVAL = 1000;
    private Email currentDisplayedEmail;
    private String currentFilter = "Email ricevute";
    private boolean initialLoadCompleted = false;
    private volatile boolean isShuttingDown = false;
    private volatile boolean alertShown = false;

    public ClientController() {
        this.mailbox = new Mailbox("");
        this.executorService = Executors.newCachedThreadPool();
        this.connectedProperty = new SimpleBooleanProperty(false);
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

    private void filterEmails(String filter) {
        if (!isConnected()) {
            showErrorAlert("Server Disconnesso", "Impossibile filtrare le email: server non raggiungibile");
            return;
        }

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            String command = filter.equals("Email inviate") ? "FETCH_SENT_EMAILS" : "FETCH_RECEIVED_EMAILS";
            NetworkUtils.sendObject(socket, command);
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

            @SuppressWarnings("unchecked")
            List<Email> emails = (List<Email>) NetworkUtils.receiveObject(socket);

            Platform.runLater(() -> {
                mailbox.clearEmails();
                if (emails != null) {
                    if (filter.equals("Email inviate")) {
                        emails.forEach(mailbox::addSentEmail);
                        emailTableView.setItems(mailbox.getSentEmails());
                    } else {
                        emails.forEach(mailbox::addReceivedEmail);
                        emailTableView.setItems(mailbox.getReceivedEmails());
                    }
                    emailTableView.refresh();
                }
            });
        } catch (Exception e) {
            handleConnectionError();
        }
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

    private void startEmailFetcher() {
        executorService.submit(() -> {

            if (isConnected()) {
                fetchEmails();
            }

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000);
                    if (isConnected()) {
                        fetchEmails();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }


    @FXML
    private void handleEmailSelection(Email email) {
        if (!isConnected()) {
            showErrorAlert("Server non raggiungibile", "Impossibile aprire l'email: server non disponibile");
            return;
        }

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "MARK_AS_READ");
            NetworkUtils.sendObject(socket, email.getId());
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

            String response = (String) NetworkUtils.receiveObject(socket);
            if ("OK".equals(response)) {
                email.setRead(true);
                displayEmailDetails(email);
            } else {
                showErrorAlert("Errore", "Impossibile aprire l'email");
            }
        } catch (Exception e) {
            showErrorAlert("Errore di connessione", "Impossibile contattare il server");
        }
    }

    private void setupConnectionListener() {
        connectedProperty.addListener((observable, oldValue, newValue) -> {
            connectionStatusLabel.setText(newValue ? "Connected" : "Disconnected");
            connectionStatusLabel.setStyle(newValue ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
        });
    }

    public void setEmailAddress(String emailAddress) {
        mailbox.setEmailAddress(emailAddress);
        emailAddressLabel.setText(emailAddress);
    }

    private boolean checkServerAvailability() {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "CHECK_COMPOSE");
            String response = (String) NetworkUtils.receiveObject(socket);
            return "OK".equals(response);
        } catch (Exception e) {
            Platform.runLater(() -> {
                showErrorAlert("Server non disponibile",
                        "Impossibile aprire l'editor: server non raggiungibile");
            });
            return false;
        }
    }

    @FXML
    private void handleComposeEmail() {
        // Remove server availability check
        Platform.runLater(() -> {
            clearComposeFields();
            showComposeView();
        });
    }

    @FXML
    private void handleSendEmail() {
        if (!isConnected()) {
            showErrorAlert("Server Disconnesso", "Impossibile inviare l'email: server non raggiungibile");
            return;
        }

        // Remove field validation here
        try {
            Email newEmail = createEmailFromFields();
            try (Socket controlSocket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(controlSocket, "REQUEST_WRITE_SOCKET");
                int dedicatedPort = (int) NetworkUtils.receiveObject(controlSocket);

                try (Socket dedicatedSocket = new Socket(SERVER_ADDRESS, dedicatedPort)) {
                    NetworkUtils.sendObject(dedicatedSocket, newEmail);
                    NetworkUtils.sendObject(dedicatedSocket, mailbox.getEmailAddress());

                    String response = (String) NetworkUtils.receiveObject(dedicatedSocket);
                    if ("OK".equals(response)) {
                        showEmailListView();
                        showInfoAlert("Email Inviata", "Email inviata con successo");
                        fetchEmails();
                    }
                    else {
                        String cleanedResponse = response.replace("ERROR: ", "");
                        showErrorAlert("Errore", "Impossibile inviare l'email: " + cleanedResponse);
                    }
                }
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
            Socket socket = new Socket("localhost", 5000);
            NetworkUtils.sendObject(socket, "REPLY");
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());
            NetworkUtils.sendObject(socket, currentDisplayedEmail.getId());

            String response = (String) NetworkUtils.receiveObject(socket);
            if ("OK".equals(response)) {
                Email replyTemplate = (Email) NetworkUtils.receiveObject(socket);
                replyTemplate.setSender(mailbox.getEmailAddress());
                populateComposeFields(replyTemplate);
                showComposeView();
            } else {
                showErrorAlert("Errore", "Impossibile creare la risposta");
            }
        } catch (IOException | ClassNotFoundException e) {
            showErrorAlert("Errore di Connessione", "Impossibile connettersi al server");
        }
    }

    private void populateComposeFields(Email email) {

        toField.clear();
        subjectField.setText(email.getSubject() != null ? email.getSubject() : "");

        if (email.getRecipients() != null && !email.getRecipients().isEmpty()) {
            toField.setText(String.join(", ", email.getRecipients()));
        }

        bodyArea.setText(email.getBody() != null ? email.getBody() : "");
    }

    @FXML
    private void handleReplyAllEmail() {
        if (currentDisplayedEmail == null) {
            showErrorAlert("Nessuna Selezione", "Seleziona un'email per rispondere a tutti");
            return;
        }

        try {
            Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            NetworkUtils.sendObject(socket, "REPLY_ALL");
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());
            NetworkUtils.sendObject(socket, currentDisplayedEmail.getId());

            String response = (String) NetworkUtils.receiveObject(socket);
            if ("OK".equals(response)) {
                Email replyAllTemplate = (Email) NetworkUtils.receiveObject(socket);
                replyAllTemplate.setSender(mailbox.getEmailAddress());
                populateComposeFields(replyAllTemplate);
                showComposeView();
            } else {
                showErrorAlert("Errore", "Impossibile creare la risposta a tutti");
            }
        } catch (IOException | ClassNotFoundException e) {
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
            Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            NetworkUtils.sendObject(socket, "FORWARD");
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());
            NetworkUtils.sendObject(socket, currentDisplayedEmail.getId());

            String response = (String) NetworkUtils.receiveObject(socket);
            if ("OK".equals(response)) {
                Email forwardTemplate = (Email) NetworkUtils.receiveObject(socket);
                forwardTemplate.setSender(mailbox.getEmailAddress());
                populateComposeFields(forwardTemplate);
                showComposeView();
            } else {
                showErrorAlert("Errore", "Impossibile creare l'inoltro");
            }
        } catch (IOException | ClassNotFoundException e) {
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
                try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                    NetworkUtils.sendObject(socket, "DELETE_EMAIL");
                    NetworkUtils.sendObject(socket, mailbox.getEmailAddress());
                    NetworkUtils.sendObject(socket, currentDisplayedEmail.getId());

                    String response = (String) NetworkUtils.receiveObject(socket);

                    if ("OK".equals(response)) {
                        mailbox.removeEmail(currentDisplayedEmail);
                        showEmailListView();
                        showInfoAlert("Email Eliminata", "Email eliminata con successo");
                    } else {
                        showErrorAlert("Errore", "Impossibile eliminare l'email");
                    }
                } catch (Exception e) {
                    handleConnectionError();
                }
            }
        }
    }

    private void fetchEmails() {
        List<Email> fetchedReceivedEmails = null;
        List<Email> fetchedSentEmails = null;

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "FETCH_RECEIVED_EMAILS");
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

            fetchedReceivedEmails = (List<Email>) NetworkUtils.receiveObject(socket);
        } catch (Exception e) {
            handleConnectionError();
            return;
        }

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "FETCH_SENT_EMAILS");
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

            fetchedSentEmails = (List<Email>) NetworkUtils.receiveObject(socket);

            final List<Email> receivedEmails = fetchedReceivedEmails;
            final List<Email> sentEmails = fetchedSentEmails;

            Platform.runLater(() -> {
                mailbox.clearEmails();

                if (receivedEmails != null) {
                    receivedEmails.forEach(mailbox::addReceivedEmail);
                }

                if (sentEmails != null) {
                    sentEmails.forEach(mailbox::addSentEmail);
                }


                if (currentFilter.equals("Email ricevute")) {
                    emailTableView.setItems(mailbox.getReceivedEmails());
                } else {
                    emailTableView.setItems(mailbox.getSentEmails());
                }
                emailTableView.refresh();
            });
        } catch (Exception e) {
            handleConnectionError();
        }
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

    private void startPolling() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(POLLING_INTERVAL), e -> pollForNewEmails()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void pollForNewEmails() {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "CHECK_NEW_EMAILS");
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

            @SuppressWarnings("unchecked")
            List<Email> newEmails = (List<Email>) NetworkUtils.receiveObject(socket);

            if (newEmails != null && !newEmails.isEmpty()) {
                Platform.runLater(() -> {
                    boolean wasDetailViewVisible = emailDetailTextArea.isVisible();
                    boolean wasComposeViewVisible = composeView.isVisible();
                    Email selectedEmail = currentDisplayedEmail;

                    if (currentFilter.equals("Email ricevute")) {
                        newEmails.forEach(mailbox::addReceivedEmail);
                        emailTableView.setItems(mailbox.getReceivedEmails());
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

    private boolean validateFields() {
        String recipients = toField.getText().trim();
        if (recipients.isEmpty()) {
            showErrorAlert("Campo Mancante", "Il campo 'A:' è obbligatorio");
            return false;
        }

        if (subjectField.getText().trim().isEmpty()) {
            showErrorAlert("Campo Mancante", "Il campo 'Oggetto' è obbligatorio");
            return false;
        }

        if (bodyArea.getText().trim().isEmpty()) {
            showErrorAlert("Campo Mancante", "Il corpo dell'email è obbligatorio");
            return false;
        }

        return true;
    }

    public void checkConnection() {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "PING");
            String response = (String) NetworkUtils.receiveObject(socket);
            boolean isConnected = "PONG".equals(response);
            Platform.runLater(() -> {
                connectedProperty.set(isConnected);
                if (isConnected) {
                    alertShown = false;
                }
            });
        } catch (Exception e) {
            handleConnectionError();
        }
    }

    private void startConnectionChecker() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted() && !isShuttingDown) {
                checkConnection();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private Email createEmailFromFields() {
        Email email = new Email();
        email.setRecipients(Arrays.asList(toField.getText().split("\\s*,\\s*")));
        email.setSubject(subjectField.getText());
        email.setBody(bodyArea.getText());
        email.setSender(mailbox.getEmailAddress());
        return email;
    }

    private void clearComposeFields() {
        toField.clear();
        subjectField.clear();
        bodyArea.clear();
    }

    private void handleConnectionError() {
        if (!alertShown) {
            Platform.runLater(() -> {
                connectedProperty.set(false);
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

    public boolean isConnected() {
        return connectedProperty.get();
    }

    public void shutdown() {
        executorService.shutdown();
    }
}