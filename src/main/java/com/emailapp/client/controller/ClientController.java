package com.emailapp.client.controller;

import com.emailapp.util.NetworkUtils;
import com.emailapp.util.EmailFileManager;
import com.emailapp.client.model.Email;
import com.emailapp.client.model.Mailbox;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.net.ConnectException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ClientController {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final long ALERT_INTERVAL_SECONDS = 30;
    private Timeline autoRefreshTimeline;
    @FXML private Label emailAddressLabel;
    @FXML private Label connectionStatusLabel;
    @FXML private TableView<Email> emailTableView;
    @FXML private TableColumn<Email, String> senderColumn;
    @FXML private TableColumn<Email, String> subjectColumn;
    @FXML private TableColumn<Email, String> dateColumn;
    @FXML private TextField toField;
    @FXML private TextField subjectField;
    @FXML private TextArea bodyArea;
    @FXML private VBox emailDetailView; // VBox for email details
    @FXML private Label emailDetailLabel; // Label for email details
    @FXML private HBox actionButtons; // HBox for action buttons (Reply, Reply All, Forward, Delete)
    @FXML private TextFlow emailDetailFlow;
    @FXML private VBox composeView; // For composing email
    @FXML private StackPane detailOrComposeStack; // The StackPane that contains both views
    @FXML private Button sendButton;
    @FXML private TextArea emailDetailTextArea;


    private final Mailbox mailbox;
    private static ExecutorService executorService;
    private final BooleanProperty connectedProperty;
    private Instant lastAlertTime = Instant.MIN;
    private Set<String> validEmails;
    private final Object lock = new Object();

    public ClientController() {
        this.mailbox = new Mailbox("");
        executorService = Executors.newCachedThreadPool();
        this.connectedProperty = new SimpleBooleanProperty(false);
        loadValidEmails();
    }

    @FXML
    public void initialize() {
        mailbox.setEmailLoadedCallback(this::refreshEmailTable);

        // Set up cell value factories for all columns
        senderColumn.setCellValueFactory(new PropertyValueFactory<>("sender"));
        subjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        dateColumn.setCellValueFactory(cellData -> {
            Email email = cellData.getValue();
            String formattedDate = email.getSentDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            return javafx.beans.binding.Bindings.createStringBinding(() -> formattedDate);
        });

        // Set up cell factories for all columns
        senderColumn.setCellFactory(this::createStyledCell);
        subjectColumn.setCellFactory(this::createStyledCell);
        dateColumn.setCellFactory(this::createStyledCell);

        emailTableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        emailTableView.setItems(mailbox.getAllEmails());
        emailTableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) { // Detect double-click
                handleEmailSelection();
            }
        });

        connectedProperty.addListener((observable, oldValue, newValue) -> {
            connectionStatusLabel.setText(newValue ? "Connected" : "Disconnected");
            connectionStatusLabel.setStyle(newValue ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
        });

        emailTableView.setVisible(true);
        startConnectionChecker();
        loadEmailsFromDisk();
        setupAutoRefresh();
    }

    private <T> TableCell<Email, T> createStyledCell(TableColumn<Email, T> column) {
        return new TableCell<Email, T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    Email email = getTableView().getItems().get(getIndex());
                    if (!email.isRead()) {
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        };
    }


    private void refreshEmailTable() {
        Platform.runLater(() -> {
            emailTableView.setItems(null);
            emailTableView.setItems(mailbox.getAllEmails());
            emailTableView.refresh();
        });
    }

    private void setupAutoRefresh() {
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            if (isConnected()) {
                fetchNewEmails();
            }
        }));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();
    }

    private void loadValidEmails() {
        validEmails = new HashSet<>();
        try (InputStream inputStream = getClass().getResourceAsStream("/emails.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                validEmails.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Error", "Failed to load valid email addresses from emails.txt.");
        } catch (NullPointerException e) {
            e.printStackTrace();
            showErrorAlert("Error", "emails.txt file not found in resources.");
        }
    }

    public void checkConnection() {
        Socket socket = null;
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            NetworkUtils.sendObject(socket, "PING");
            String response = (String) NetworkUtils.receiveObject(socket);
            boolean isConnected = "PONG".equals(response);
            Platform.runLater(() -> {
                connectedProperty.set(isConnected);
            });
        } catch (Exception e) {
            Platform.runLater(() -> {
                connectedProperty.set(false);
            });
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Errore chiusura socket: " + e.getMessage());
                }
            }
        }
    }




    public void setEmailAddress(String emailAddress) {
        mailbox.setEmailAddress(emailAddress);
        emailAddressLabel.setText(emailAddress);
        mailbox.loadEmailsFromDisk();
    }

    @FXML
    private void handleEmailSelection() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
            displayEmailDetails(selectedEmail);
            if (!selectedEmail.isRead()) {
                markEmailAsRead(selectedEmail);
            }
        }
    }

    private void markEmailAsRead(Email email) {
        email.setRead(true);
        emailTableView.refresh();
        executorService.submit(() -> {
            try {
                EmailFileManager.markEmailAsRead(email.getId(), mailbox.getEmailAddress());
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> showErrorAlert("Error", "Failed to mark email as read"));
            }
        });
    }

    @FXML
    private void handleComposeEmail() {
        // Reset dei campi
        toField.clear();
        subjectField.clear();
        bodyArea.clear();

        // Rendi il campo destinatario modificabile
        toField.setEditable(true);

        // Mostra la vista di composizione
        emailDetailFlow.setVisible(false);
        composeView.setVisible(true);
        detailOrComposeStack.getChildren().setAll(composeView);

        // Binding delle dimensioni
        composeView.prefWidthProperty().bind(detailOrComposeStack.widthProperty());
        composeView.prefHeightProperty().bind(detailOrComposeStack.heightProperty());

        // Reimposta l'handler per il pulsante di invio
        sendButton.setOnAction(event -> {
            handleSendEmail();
            returnToEmailListView();
        });
    }

    private void displayEmailDetails(Email email) {
        StringBuilder emailDetails = new StringBuilder();
        emailDetails.append("From: ").append(email.getSender()).append("\n");
        emailDetails.append("To: ").append(String.join(", ", email.getRecipients())).append("\n");
        emailDetails.append("Subject: ").append(email.getSubject()).append("\n");
        emailDetails.append("Date: ").append(email.getSentDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n");
        emailDetails.append("Body: ").append(email.getBody());

        emailTableView.setVisible(false);
        detailOrComposeStack.getChildren().setAll(emailDetailFlow);
        emailDetailTextArea.setText(emailDetails.toString());
        emailDetailTextArea.setEditable(false);
        emailDetailTextArea.setWrapText(true);
        emailDetailFlow.setVisible(true);
        actionButtons.setVisible(true);

    }

    @FXML
    private void handleCancelEmail() {
        composeView.setVisible(false);
        clearComposeFields();
        actionButtons.setVisible(false); // Hide action buttons when cancelling compose
    }

    @FXML
    private void handleBackButton() {
        // Hide email details and compose view
        emailDetailFlow.setVisible(false);
        composeView.setVisible(false);

        // Hide action buttons
        actionButtons.setVisible(false);

        // Ensure the email table view is visible
        emailTableView.setVisible(true);

        // Clear the right side of the interface
        detailOrComposeStack.getChildren().clear();

        // Refresh the email table
        refreshEmailTable();
    }

    private void loadEmailsFromDisk() {
        executorService.submit(() -> {
            try {
                // Usa il corretto indirizzo email dell'utente
                String userEmail = mailbox.getEmailAddress();
                List<Email> loadedEmails = EmailFileManager.loadEmails(userEmail);
                Platform.runLater(() -> {
                    mailbox.clearEmails(); // Pulisce le email esistenti prima di aggiungere quelle caricate
                    for (Email email : loadedEmails) {
                        mailbox.addReceivedEmail(email);
                    }
                    emailTableView.refresh(); // Aggiorna la vista della tabella
                });
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> showErrorAlert("Load Error", "Failed to load emails from disk"));
            }
        });
    }


    @FXML
    private synchronized void handleSendEmail() {
        if (validateFields()) {
            String recipientsString = toField.getText().trim();
            List<String> recipients = Arrays.asList(recipientsString.split("\\s*,\\s*"));

            boolean allValid = recipients.stream().allMatch(this::isValidRecipient);

            if (!allValid) {
                showErrorAlert("Indirizzo inesistente", "Uno o più indirizzi email forniti non sono presenti in emails.txt");
                return;
            }

            Email newEmail = new Email();
            newEmail.setSender(mailbox.getEmailAddress());
            newEmail.setRecipients(recipients);
            newEmail.setSubject(subjectField.getText());
            newEmail.setBody(bodyArea.getText());

            synchronized (lock) {
                sendEmail(newEmail);
            }

            composeView.setVisible(false);
            clearComposeFields();

            returnToEmailListView();
        }
    }


    private boolean isValidRecipient(String email) {
        return validEmails.contains(email.trim());
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

    private synchronized void sendEmail(Email email) {
        Socket socket = null;
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            NetworkUtils.sendObject(socket, "SEND_EMAIL");
            NetworkUtils.sendObject(socket, email);
            String response = (String) NetworkUtils.receiveObject(socket);

            Platform.runLater(() -> {
                if ("OK".equals(response)) {
                    mailbox.addSentEmail(email);
                    refreshEmailTable();
                    showInfoAlert("Email Sent", "Email successfully sent to " + email.getRecipients());
                } else {
                    showErrorAlert("Error", "Failed to send email: " + response);
                }
            });
        } catch (Exception e) {
            handleConnectionError(e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Errore chiusura socket: " + e.getMessage());
                }
            }
        }
    }

    private synchronized void fetchNewEmails() {
        Socket socket = null;
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            NetworkUtils.sendObject(socket, "FETCH_NEW_EMAILS");
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());
            @SuppressWarnings("unchecked")
            List<Email> newEmails = (List<Email>) NetworkUtils.receiveObject(socket);

            Platform.runLater(() -> {
                synchronized (lock) {
                    int newEmailCount = 0;
                    for (Email email : newEmails) {
                        if (!mailbox.hasEmail(Long.parseLong(String.valueOf(email.getId())))) {
                            mailbox.addReceivedEmail(email);
                            newEmailCount++;
                        }
                    }
                    if (newEmailCount > 0) {
                        refreshEmailTable();
                        showInfoAlert("New Emails for: " + mailbox.getEmailAddress(),
                                "Received " + newEmailCount + " new email(s)");
                    }
                }
            });
        } catch (Exception e) {
            handleConnectionError(e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Errore chiusura socket: " + e.getMessage());
                }
            }
        }
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
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Conferma Eliminazione");
            confirmAlert.setHeaderText("Elimina Email");
            confirmAlert.setContentText("Sei sicuro di voler eliminare questa email?");

            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                deleteEmail(selectedEmail);
            }
        }
    }


    private void deleteEmail(Email email) {
        executorService.submit(() -> {
            try {
                // Attempt to delete the email file locally
                boolean locallyDeleted = EmailFileManager.deleteEmail(email.getId(), mailbox.getEmailAddress());

                if (locallyDeleted) {
                    // If local deletion was successful, attempt to delete from the server
                    boolean deletedFromServer = deleteEmailFromServer(email);

                    Platform.runLater(() -> {
                        // Remove the email from the mailbox and update the UI
                        mailbox.removeEmail(email);
                        refreshEmailTable();
                        returnToEmailListView();

                        // Show success message
                        showInfoAlert("Email Eliminata", "L'email è stata eliminata con successo.");
                    });
                } else {
                    Platform.runLater(() -> {
                        showErrorAlert("Errore di Eliminazione", "Impossibile eliminare l'email. Riprova più tardi.");
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showErrorAlert("Errore di Eliminazione", "Si è verificato un errore durante l'eliminazione dell'email: " + e.getMessage());
                });
            }
        });
    }

    private boolean deleteEmailFromServer(Email email) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            socket.setSoTimeout(30000); // 30 seconds timeout

            NetworkUtils.sendObject(socket, "DELETE_EMAIL");
            NetworkUtils.sendObject(socket, email.getId());
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

            Object response = NetworkUtils.receiveObject(socket);
            if (response instanceof Boolean) {
                return (Boolean) response;
            } else {
                System.err.println("Unexpected response type from server: " + response.getClass().getName());
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error deleting email from server: " + e.getMessage());
            return false;
        }
    }

    private boolean deleteLocalEmailFile(int emailId) {
        String userDirectory = System.getProperty("user.home") + File.separator + "EmailApp" + File.separator + mailbox.getEmailAddress();
        File emailFile = new File(userDirectory, "email_" + emailId + ".txt");

        if (emailFile.exists()) {
            return emailFile.delete();
        } else {
            return false;
        }
    }

    private void showWarningAlert(String title, String content) {
        showAlert(Alert.AlertType.WARNING, title, content);
    }



    private void openComposeWindow(Email selectedEmail, String mode) {
        composeView.setVisible(true);
        emailDetailFlow.setVisible(false);
        emailTableView.setVisible(false);
        actionButtons.setVisible(false);

        if (selectedEmail != null) {
            // Set the recipient(s)
            if (mode.equals("Reply")) {
                toField.setText(selectedEmail.getSender());
            } else if (mode.equals("Reply All")) {
                List<String> allRecipients = new ArrayList<>(selectedEmail.getRecipients());
                allRecipients.add(selectedEmail.getSender());
                allRecipients.remove(mailbox.getEmailAddress()); // Remove the current user's email
                toField.setText(String.join(", ", allRecipients));
            } else if (mode.equals("Forward")) {
                toField.clear(); // Clear the recipient field for forward
            }
            toField.setEditable(mode.equals("Forward")); // Make the recipient field editable only for Forward

            // Set the subject
            String subjectPrefix = mode.equals("Forward") ? "Fwd: " : "Re: ";
            subjectField.setText(subjectPrefix + selectedEmail.getSubject());

            // Set the body
            StringBuilder bodyBuilder = new StringBuilder();
            if (mode.equals("Forward")) {
                bodyBuilder.append("\n\n---------- Forwarded message ---------\n");
                bodyBuilder.append("From: ").append(selectedEmail.getSender()).append("\n");
                bodyBuilder.append("Date: ").append(selectedEmail.getSentDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n");
                bodyBuilder.append("Subject: ").append(selectedEmail.getSubject()).append("\n");
                bodyBuilder.append("To: ").append(String.join(", ", selectedEmail.getRecipients())).append("\n\n");
                bodyBuilder.append(selectedEmail.getBody());
            } else {
                String originalBody = selectedEmail.getBody();
                String quotedBody = originalBody.lines()
                        .map(line -> "> " + line)
                        .collect(Collectors.joining("\n"));
                String headerLine = String.format("On %s, %s wrote:",
                        selectedEmail.getSentDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        selectedEmail.getSender());
                bodyBuilder.append("\n\n").append(headerLine).append("\n").append(quotedBody);
            }
            bodyArea.setText(bodyBuilder.toString());

            // Place the cursor at the beginning of the body
            bodyArea.positionCaret(0);
        }

        emailTableView.setVisible(true); // Ensure table view remains visible
        detailOrComposeStack.getChildren().setAll(composeView);
        composeView.prefWidthProperty().bind(detailOrComposeStack.widthProperty());
        composeView.prefHeightProperty().bind(detailOrComposeStack.heightProperty());

        sendButton.setOnAction(event -> {
            handleSendEmail();
            returnToEmailListView();
        });
    }

    @FXML
    private void handleBackInCompose() {
        returnToEmailListView();
    }

    private void returnToEmailListView() {
        // Hide compose and detail views
        composeView.setVisible(false);
        emailDetailFlow.setVisible(false);
        actionButtons.setVisible(false);

        // Show email table view (should already be visible, but ensure it is)
        emailTableView.setVisible(true);

        // Clear the right side of the interface
        detailOrComposeStack.getChildren().clear();

        // Reset the layout
        emailTableView.setManaged(true);
        emailTableView.setMaxWidth(Double.MAX_VALUE);
        emailTableView.setMaxHeight(Double.MAX_VALUE);

        // Clear compose fields
        clearComposeFields();

        // Refresh the email table
        refreshEmailTable();

        // Request layout update
        emailTableView.requestLayout();
        detailOrComposeStack.requestLayout();
    }

    public boolean isConnected() {
        return connectedProperty.get();
    }

    private void handleConnectionError(Exception e) {
        Platform.runLater(() -> {
            connectedProperty.set(false);
        });
    }

    private void showServerClosedAlert() {
        showErrorAlert("Server Chiuso", "Attualmente il server è chiuso. Impossibile inviare e ricevere");
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

    public void shutdown() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
    }
}