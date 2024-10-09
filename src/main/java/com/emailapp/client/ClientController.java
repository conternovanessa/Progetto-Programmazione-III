package com.emailapp.client;

import com.emailapp.NetworkUtils;
import com.emailapp.EmailFileManager;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.Socket;
import java.net.ConnectException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientController {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    @FXML private Label emailAddressLabel;
    @FXML private Label connectionStatusLabel;
    @FXML private TableView<Email> emailTableView;
    @FXML private TableColumn<Email, String> senderColumn;
    @FXML private TableColumn<Email, String> subjectColumn;
    @FXML private TableColumn<Email, String> dateColumn;
    @FXML private VBox composeView;
    @FXML private TextField toField;
    @FXML private TextField subjectField;
    @FXML private TextArea bodyArea;

    private final Mailbox mailbox;
    private static ExecutorService executorService;
    private final BooleanProperty connectedProperty;
    private static final long ALERT_INTERVAL_SECONDS = 30;
    private Instant lastAlertTime = Instant.MIN;

    public ClientController() {
        this.mailbox = new Mailbox("");
        executorService = Executors.newCachedThreadPool();
        this.connectedProperty = new SimpleBooleanProperty(false);
    }

    @FXML
    public void initialize() {
        senderColumn.setCellValueFactory(new PropertyValueFactory<>("sender"));
        subjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        dateColumn.setCellValueFactory(cellData -> {
            Email email = cellData.getValue();
            String formattedDate = email.getSentDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            return javafx.beans.binding.Bindings.createStringBinding(() -> formattedDate);
        });

        emailTableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        emailTableView.setItems(mailbox.getAllEmails());

        connectedProperty.addListener((observable, oldValue, newValue) -> {
            connectionStatusLabel.setText(newValue ? "Connected" : "Disconnected");
            connectionStatusLabel.setStyle(newValue ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
        });

        startConnectionChecker();
        loadEmailsFromDisk();
    }

    public void checkConnection() {
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "PING");
                String response = (String) NetworkUtils.receiveObject(socket);
                boolean isConnected = "PONG".equals(response);
                Platform.runLater(() -> {
                    connectedProperty.set(isConnected);
                    if (!isConnected) {
                        showDisconnectionAlert();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    connectedProperty.set(false);
                    showDisconnectionAlert();
                });
            }
        });
    }

    private void showDisconnectionAlert() {
        Instant now = Instant.now();
        if (ChronoUnit.SECONDS.between(lastAlertTime, now) >= ALERT_INTERVAL_SECONDS) {
            handleConnectionError(new Exception("Server disconnected"));
            lastAlertTime = now;
        }
    }

    public void setEmailAddress(String emailAddress) {
        mailbox.setEmailAddress(emailAddress);
        emailAddressLabel.setText(emailAddress);
    }

    private void loadEmailsFromDisk() {
        executorService.submit(() -> {
            try {
                List<Email> loadedEmails = EmailFileManager.loadEmails(mailbox.getEmailAddress());
                Platform.runLater(() -> {
                    for (Email email : loadedEmails) {
                        mailbox.addReceivedEmail(email);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> showErrorAlert("Load Error", "Failed to load emails from disk"));
            }
        });
    }

    @FXML
    private void handleComposeEmail() {
        composeView.setVisible(true);
        clearComposeFields();
    }

    @FXML
    private void handleSendEmail() {
        if (validateFields()) {
            Email newEmail = new Email();
            newEmail.setSender(mailbox.getEmailAddress());
            newEmail.setRecipients(Collections.singletonList(toField.getText()));
            newEmail.setSubject(subjectField.getText());
            newEmail.setBody(bodyArea.getText());

            sendEmail(newEmail);
            composeView.setVisible(false);
            clearComposeFields();
        }
    }

    @FXML
    private void handleCancelEmail() {
        composeView.setVisible(false);
        clearComposeFields();
    }

    private void clearComposeFields() {
        toField.clear();
        subjectField.clear();
        bodyArea.clear();
    }

    private boolean validateFields() {
        if (toField.getText().isEmpty()) {
            showErrorAlert("Invalid Recipient", "Please enter a recipient email address.");
            return false;
        }
        if (subjectField.getText().isEmpty()) {
            showErrorAlert("Missing Subject", "Please enter a subject for your email.");
            return false;
        }
        if (bodyArea.getText().isEmpty()) {
            showErrorAlert("Empty Message", "Please enter a message in the email body.");
            return false;
        }
        return true;
    }

    private void sendEmail(Email email) {
        if (!isConnected()) {
            showServerClosedAlert();
            return;
        }

        if (email.isEmpty()) {
            showErrorAlert("Send Error", "Cannot send an empty email");
            return;
        }

        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                EmailFileManager.saveEmail(email, mailbox.getEmailAddress());
                NetworkUtils.sendObject(socket, "SEND_EMAIL");
                NetworkUtils.sendObject(socket, email);
                String response = (String) NetworkUtils.receiveObject(socket);
                if ("SUCCESS".equals(response)) {
                    Platform.runLater(() -> mailbox.addSentEmail(email));
                } else {
                    Platform.runLater(() -> showErrorAlert("Send Error", "Failed to send email"));
                }
            } catch (ConnectException e) {
                Platform.runLater(this::showServerClosedAlert);
            } catch (Exception e) {
                handleConnectionError(e);
                e.printStackTrace();
                Platform.runLater(() -> showErrorAlert("Save Error", "Failed to save email to disk"));
            }
        });
    }

    @FXML
    private void handleRefreshEmails() {
        fetchNewEmails();
    }

    private void fetchNewEmails() {
        if (!isConnected()) {
            showServerClosedAlert();
            return;
        }

        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "FETCH_NEW_EMAILS");
                NetworkUtils.sendObject(socket, mailbox.getEmailAddress());
                @SuppressWarnings("unchecked")
                List<Email> newEmails = (List<Email>) NetworkUtils.receiveObject(socket);
                Platform.runLater(() -> {
                    int newEmailCount = 0;
                    for (Email email : newEmails) {
                        if (!mailbox.hasEmail(email.getId())) {
                            mailbox.addReceivedEmail(email);
                            newEmailCount++;
                        }
                    }
                    showInfoAlert("New Emails", "Received " + newEmailCount + " new email(s)");
                });
            } catch (ConnectException e) {
                Platform.runLater(this::showServerClosedAlert);
            } catch (Exception e) {
                handleConnectionError(e);
            }
        });
    }

    @FXML
    private void handleReplyEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            openComposeWindow(selectedEmail, "Reply");
        }
    }

    @FXML
    private void handleReplyAllEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            openComposeWindow(selectedEmail, "Reply All");
        }
    }

    @FXML
    private void handleForwardEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            openComposeWindow(selectedEmail, "Forward");
        }
    }

    @FXML
    private void handleDeleteEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            deleteEmail(selectedEmail);
        }
    }

    private void openComposeWindow(Email selectedEmail, String mode) {
        composeView.setVisible(true);
        if (selectedEmail != null) {
            if (!selectedEmail.getRecipients().isEmpty()) {
                toField.setText(selectedEmail.getRecipients().get(0));
            }
            if (selectedEmail.getSubject() != null) {
                subjectField.setText(mode.equals("Forward") ? "Fwd: " + selectedEmail.getSubject() : "Re: " + selectedEmail.getSubject());
            }
            if (selectedEmail.getBody() != null) {
                bodyArea.setText("\n\n--- Original Message ---\n" + selectedEmail.getBody());
            }
        }
    }

    private void deleteEmail(Email email) {
        if (!isConnected()) {
            showServerClosedAlert();
            return;
        }

        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "DELETE_EMAIL");
                NetworkUtils.sendObject(socket, email.getId());
                String response = (String) NetworkUtils.receiveObject(socket);
                if ("SUCCESS".equals(response)) {
                    Platform.runLater(() -> mailbox.removeEmail(email));
                    EmailFileManager.deleteEmail(email.getId(), mailbox.getEmailAddress());
                } else {
                    Platform.runLater(() -> showErrorAlert("Delete Error", "Failed to delete email"));
                }
            } catch (ConnectException e) {
                Platform.runLater(this::showServerClosedAlert);
            } catch (Exception e) {
                handleConnectionError(e);
                Platform.runLater(() -> showErrorAlert("Delete Error", "Failed to delete email from disk"));
            }
        });
    }


    public boolean isConnected() {
        return connectedProperty.get();
    }

    private void handleConnectionError(Exception e) {
        Platform.runLater(() -> {
            connectedProperty.set(false);
            showErrorAlert("Connection Error", "Failed to connect to the server: " + e.getMessage() + "\nPlease try again later.");
        });
    }

    private void showServerClosedAlert() {
        showErrorAlert("Server Closed", "The server is currently closed. Please try again later.");
    }

    private void showErrorAlert(String title, String content) {
        showAlert(Alert.AlertType.ERROR, title, content);
    }

    private void showInfoAlert(String title, String content) {
        showAlert(Alert.AlertType.INFORMATION, title, content);
    }

    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void startConnectionChecker() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                checkConnection();
                try {
                    Thread.sleep(5000); // Check connection every 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    public static void shutdown() {
        // This method should be called when the application is closing
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}
