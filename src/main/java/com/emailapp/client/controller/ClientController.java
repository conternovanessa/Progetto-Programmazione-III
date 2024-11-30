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

    public ClientController() {
        this.mailbox = new Mailbox("");
        this.executorService = Executors.newCachedThreadPool();
        this.connectedProperty = new SimpleBooleanProperty(false);
    }

    @FXML
    public void initialize() {
        setupConnectionListener();
        startConnectionChecker();
        setupEmailTableView();
        setupEmailFilter();
        startEmailFetcher();
        startPolling();
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
                    } else {
                        emails.forEach(mailbox::addReceivedEmail);
                    }
                    emailTableView.setItems(mailbox.getAllEmails());
                    emailTableView.refresh();
                }
            });
        } catch (Exception e) {
            handleConnectionError();
        }
    }

    private void setupEmailTableView() {
        // Create and configure table columns
        TableColumn<Email, String> senderColumn = new TableColumn<>("From");
        senderColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSender()));

        TableColumn<Email, String> subjectColumn = new TableColumn<>("Subject");
        subjectColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSubject()));

        TableColumn<Email, String> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getSentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        ));

        // Set columns to table
        emailTableView.getColumns().setAll(senderColumn, subjectColumn, dateColumn);

        // Set selection listener
        emailTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                handleEmailSelection(newSelection);
            }
        });
    }

    private void startEmailFetcher() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (isConnected()) {
                    fetchEmails();
                }
                try {
                    Thread.sleep(5000);
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
        if (!isConnected()) {
            showErrorAlert("Server Disconnesso", "Impossibile aprire l'editor: server non raggiungibile");
            return;
        }

        executorService.submit(() -> {
            if (checkServerAvailability()) {
                Platform.runLater(() -> {
                    clearComposeFields();
                    showComposeView();
                });
            }
        });
    }

    @FXML
    private void handleSendEmail() {
        if (!isConnected()) {
            showErrorAlert("Server Disconnesso", "Impossibile inviare l'email: server non raggiungibile");
            return;
        }

        if (validateFields()) {
            // Il client invia solo la richiesta al server
            try {
                Email newEmail = createEmailFromFields();
                // Inviamo la richiesta al server attraverso una socket di controllo
                try (Socket controlSocket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                    NetworkUtils.sendObject(controlSocket, "REQUEST_WRITE_SOCKET");
                    // Attendiamo che il server ci fornisca una porta dedicata per la comunicazione
                    int dedicatedPort = (int) NetworkUtils.receiveObject(controlSocket);
                    // Ci connettiamo alla porta dedicata fornita dal server
                    try (Socket dedicatedSocket = new Socket(SERVER_ADDRESS, dedicatedPort)) {
                        // Procediamo con l'invio dell'email sulla socket dedicata
                        NetworkUtils.sendObject(dedicatedSocket, newEmail);
                        NetworkUtils.sendObject(dedicatedSocket, mailbox.getEmailAddress());

                        String response = (String) NetworkUtils.receiveObject(dedicatedSocket);
                        if ("OK".equals(response)) {
                            showEmailListView();
                            showInfoAlert("Email Inviata", "Email inviata con successo");
                        } else {
                            showErrorAlert("Errore", "Impossibile inviare l'email: " + response);
                        }
                    }
                }
            } catch (Exception e) {
                handleConnectionError();
            }
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
        // Clear fields first
        toField.clear();
        subjectField.setText(email.getSubject() != null ? email.getSubject() : "");

        // For forward, we don't want to populate recipients
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
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            Alert confirmDelete = new Alert(Alert.AlertType.CONFIRMATION);
            confirmDelete.setTitle("Conferma eliminazione");
            confirmDelete.setHeaderText("Eliminare questa email?");
            confirmDelete.setContentText("Questa operazione non può essere annullata.");

            Optional<ButtonType> result = confirmDelete.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                    NetworkUtils.sendObject(socket, "DELETE_EMAIL");
                    NetworkUtils.sendObject(socket, selectedEmail.getId());
                    NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

                    String response = (String) NetworkUtils.receiveObject(socket);
                    if ("OK".equals(response)) {
                        mailbox.removeEmail(selectedEmail);
                        showEmailListView();
                        showInfoAlert("Email Eliminata", "Email eliminata con successo");
                    }
                } catch (Exception e) {
                    handleConnectionError();
                }
            }
        }
    }

    private void fetchEmails() {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "FETCH_EMAILS");
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

            // Aggiungi il cast esplicito con il tipo generico
            @SuppressWarnings("unchecked")
            List<Email> emails = (List<Email>) NetworkUtils.receiveObject(socket);

            Platform.runLater(() -> {
                mailbox.clearEmails();
                if (emails != null) {
                    emails.forEach(mailbox::addReceivedEmail);
                    emailTableView.setItems(mailbox.getAllEmails());
                    emailTableView.refresh();
                }
            });
        } catch (Exception e) {
            handleConnectionError();
        }
    }

    private void displayEmailDetails(Email email) {
        Platform.runLater(() -> {
            currentDisplayedEmail = email; // Salva l'email corrente
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
                    // Save current state
                    boolean wasDetailViewVisible = emailDetailTextArea.isVisible();
                    boolean wasComposeViewVisible = composeView.isVisible();
                    Email selectedEmail = currentDisplayedEmail;

                    // Update data while maintaining current filter
                    newEmails.forEach(mailbox::addReceivedEmail);
                    filterEmails(currentFilter); // This will update the view with the correct filter

                    // Restore previous view state
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

            // Clear selection to allow reselecting the same email
            emailTableView.getSelectionModel().clearSelection();

            // Refresh the email list
            refreshEmailList();
        });
    }

    private void showComposeView() {
        isComposeViewVisible = true;
        composeView.setVisible(true);
        emailDetailFlow.setVisible(false);
        emailTableView.setVisible(false);
        emailDetailTextArea.setVisible(false);  // Aggiungi questa riga
        actionButtons.setVisible(false);        // Aggiungi questa riga
        detailOrComposeStack.getChildren().clear();  // Modifica questa parte
        detailOrComposeStack.getChildren().add(composeView);
        sendButton.setVisible(true);            // Aggiungi questa riga
    }

    private void showEmailListView() {
        Platform.runLater(() -> {
            isComposeViewVisible = false;
            composeView.setVisible(false);
            emailDetailFlow.setVisible(false);
            emailDetailTextArea.setVisible(false);
            emailTableView.setVisible(true);
            actionButtons.setVisible(false);

            // Clear selection and refresh
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

        List<String> recipientList = Arrays.asList(recipients.split("\\s*,\\s*"));
        for (String recipient : recipientList) {
            // Case 1: Check for @ symbol
            if (!recipient.contains("@")) {
                showErrorAlert("Formato Email Non Valido",
                        "L'indirizzo email '" + recipient + "' non contiene il simbolo @");
                return false;
            }

            String[] parts = recipient.split("@");
            String username = parts[0];
            String domain = parts[1];

            // Case 2: Check domain
            if (!"progetto.com".equals(domain)) {
                showErrorAlert("Dominio Non Valido",
                        "Il dominio deve essere 'progetto.com'. Dominio inserito: '" + domain + "'");
                return false;
            }

            // Case 3: Check username against emails.txt
            try {
                List<String> validUsernames = Files.readAllLines(Paths.get("email.txt"))
                        .stream()
                        .map(email -> email.split("@")[0])
                        .collect(Collectors.toList());

                if (!validUsernames.contains(username)) {
                    showErrorAlert("Username Non Valido",
                            "L'username '" + username + "' non è presente nel sistema");
                    return false;
                }
            } catch (IOException e) {
                showErrorAlert("Errore Sistema", "Impossibile verificare l'username");
                return false;
            }
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

    private boolean isValidEmailInSystem(String email) {
        try {
            List<String> validEmails = Files.readAllLines(Paths.get("email.txt"));
            return validEmails.contains(email);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void checkConnection() {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "PING");
            String response = (String) NetworkUtils.receiveObject(socket);
            Platform.runLater(() -> connectedProperty.set("PONG".equals(response)));
        } catch (Exception e) {
            Platform.runLater(() -> connectedProperty.set(false));
        }
    }

    private void startConnectionChecker() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                checkConnection();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
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
        Platform.runLater(() -> {
            connectedProperty.set(false);
            showErrorAlert("Errore di Connessione", "Impossibile connettersi al server");
        });
    }

    private void showErrorAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
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