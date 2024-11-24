package com.emailapp.client.controller;

import com.emailapp.util.NetworkUtils;
import com.emailapp.client.model.Email;
import com.emailapp.client.model.Mailbox;
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

import java.io.*;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final Mailbox mailbox;
    private final ExecutorService executorService;
    private final BooleanProperty connectedProperty;
    private boolean isComposeViewVisible = false;

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
        startEmailFetcher();
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
                    System.out.println("Fetching emails for: " + mailbox.getEmailAddress());
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
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                Email newEmail = createEmailFromFields();

                NetworkUtils.sendObject(socket, "SEND_EMAIL");
                NetworkUtils.sendObject(socket, newEmail);
                NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

                String response = (String) NetworkUtils.receiveObject(socket);
                if ("OK".equals(response)) {
                    showEmailListView();
                    showInfoAlert("Email Inviata", "Email inviata con successo");
                } else {
                    showErrorAlert("Errore", "Impossibile inviare l'email: " + response);
                }
            } catch (Exception e) {
                handleConnectionError();
            }
        }
    }

    @FXML
    private void handleReplyEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "GET_REPLY_TEMPLATE");
                NetworkUtils.sendObject(socket, selectedEmail.getId());
                NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

                String response = (String) NetworkUtils.receiveObject(socket);
                if (response.startsWith("OK")) {
                    // Riceve il template dal server
                    Email replyTemplate = (Email) NetworkUtils.receiveObject(socket);
                    Platform.runLater(() -> {
                        showComposeView();
                        toField.setText(replyTemplate.getRecipients().get(0));
                        subjectField.setText(replyTemplate.getSubject());
                        bodyArea.setText(replyTemplate.getBody());
                    });
                } else {
                    showErrorAlert("Errore", "Impossibile preparare la risposta: " + response);
                }
            } catch (Exception e) {
                handleConnectionError();
            }
        }
    }

    @FXML
    private void handleReplyAllEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "GET_REPLY_ALL_TEMPLATE");
                NetworkUtils.sendObject(socket, selectedEmail.getId());
                NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

                String response = (String) NetworkUtils.receiveObject(socket);
                if (response.startsWith("OK")) {
                    // Riceve il template dal server
                    Email replyAllTemplate = (Email) NetworkUtils.receiveObject(socket);
                    Platform.runLater(() -> {
                        showComposeView();
                        toField.setText(String.join(", ", replyAllTemplate.getRecipients()));
                        subjectField.setText(replyAllTemplate.getSubject());
                        bodyArea.setText(replyAllTemplate.getBody());
                    });
                } else {
                    showErrorAlert("Errore", "Impossibile preparare la risposta a tutti: " + response);
                }
            } catch (Exception e) {
                handleConnectionError();
            }
        }
    }

    @FXML
    private void handleForwardEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "GET_FORWARD_TEMPLATE");
                NetworkUtils.sendObject(socket, selectedEmail.getId());
                NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

                String response = (String) NetworkUtils.receiveObject(socket);
                if (response.startsWith("OK")) {
                    // Riceve il template dal server
                    Email forwardTemplate = (Email) NetworkUtils.receiveObject(socket);
                    Platform.runLater(() -> {
                        showComposeView();
                        toField.clear(); // Il destinatario deve essere inserito dall'utente
                        subjectField.setText(forwardTemplate.getSubject());
                        bodyArea.setText(forwardTemplate.getBody());
                    });
                } else {
                    showErrorAlert("Errore", "Impossibile preparare l'inoltro: " + response);
                }
            } catch (Exception e) {
                handleConnectionError();
            }
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
            StringBuilder details = new StringBuilder();
            details.append("Da: ").append(email.getSender()).append("\n");
            details.append("A: ").append(String.join(", ", email.getRecipients())).append("\n");
            details.append("Oggetto: ").append(email.getSubject()).append("\n\n");
            details.append(email.getBody());

            emailDetailTextArea.setText(details.toString());
            emailDetailFlow.setVisible(true);
            emailTableView.setVisible(false);
            actionButtons.setVisible(true);
        });
    }


    @FXML
    private void handleBackButton() {
        showEmailListView();
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
        isComposeViewVisible = false;
        composeView.setVisible(false);
        emailDetailFlow.setVisible(false);
        emailTableView.setVisible(true);
        refreshEmailList();
    }

    private void refreshEmailList() {
        Platform.runLater(() -> {
            emailTableView.setItems(null);
            emailTableView.setItems(mailbox.getAllEmails());
            emailTableView.refresh();
        });
    }

    private boolean validateFields() {
        if (toField.getText().isEmpty()) {
            showErrorAlert("Campo Mancante", "Inserire il destinatario");
            return false;
        }
        if (subjectField.getText().isEmpty()) {
            showErrorAlert("Campo Mancante", "Inserire l'oggetto");
            return false;
        }
        if (bodyArea.getText().isEmpty()) {
            showErrorAlert("Campo Mancante", "Inserire il testo dell'email");
            return false;
        }
        return true;
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
        email.setSender(mailbox.getEmailAddress());
        email.setRecipients(Arrays.asList(toField.getText().split("\\s*,\\s*")));
        email.setSubject(subjectField.getText());
        email.setBody(bodyArea.getText());
        return email;
    }

    private void clearComposeFields() {
        toField.clear();
        subjectField.clear();
        bodyArea.clear();
    }

    private void prepareReplyEmail(Email originalEmail) {
        showComposeView();
        toField.setText(originalEmail.getSender());
        subjectField.setText("Re: " + originalEmail.getSubject());
        bodyArea.setText("\n\n----- Messaggio Originale -----\n" + originalEmail.getBody());
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