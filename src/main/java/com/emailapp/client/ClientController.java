package com.emailapp.client;

import com.emailapp.NetworkUtils;
import com.emailapp.EmailFileManager;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
        senderColumn.setCellValueFactory(new PropertyValueFactory<>("sender"));
        senderColumn.setCellFactory(column -> createBoldCell());  // Nuova riga
        subjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        dateColumn.setCellValueFactory(cellData -> {
            Email email = cellData.getValue();
            String formattedDate = email.getSentDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            return javafx.beans.binding.Bindings.createStringBinding(() -> formattedDate);
        });

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

    private TableCell<Email, String> createBoldCell() {
        return new TableCell<Email, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
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
        emailTableView.setItems(mailbox.getAllEmails());
        emailTableView.refresh();
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
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
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
            }
        });
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
            displayEmailDetails(selectedEmail); // Display the selected email details
            emailTableView.setVisible(false); // Hide the email table view when an email is selected
            composeView.setVisible(false); // Hide the compose view
            emailDetailFlow.setVisible(true); // Show email details
            actionButtons.setVisible(true); // Show action buttons
            detailOrComposeStack.getChildren().setAll(emailDetailFlow); // Ensure only email detail view is in the StackPane
        }
    }

    @FXML
    private void handleComposeEmail() {
        emailDetailFlow.setVisible(false); // Hide email details
        composeView.setVisible(true); // Show the compose view
        detailOrComposeStack.getChildren().setAll(composeView); // Ensure only compose view is in the StackPane
    }

    private void displayEmailDetails(Email email) {
        String emailDetails = "From: " + email.getSender() + "\n" +
                "To: " + String.join(", ", email.getRecipients()) + "\n" +
                "Subject: " + email.getSubject() + "\n" +
                "Date: " + email.getSentDate().toString() + "\n" +
                "Body: " + email.getBody().trim().replaceAll("\\s+", " ");

        emailTableView.setVisible(true); // Ensure table view remains visible
        detailOrComposeStack.getChildren().setAll(emailDetailFlow);
        emailDetailLabel.setText(emailDetails); // Update the email detail label
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
                NetworkUtils.sendObject(socket, "SEND_EMAIL");
                NetworkUtils.sendObject(socket, email);
                String response = (String) NetworkUtils.receiveObject(socket);
                if ("SUCCESS".equals(response)) {
                    Platform.runLater(() -> {
                        composeView.setVisible(false);
                        clearComposeFields();
                        //showInfoAlert("Email Sent", "Your email has been sent successfully.");
                    });
                } else {
                    Platform.runLater(() -> showErrorAlert("Send Error", "Failed to send email: " + response));
                }
            } catch (ConnectException e) {
                Platform.runLater(this::showServerClosedAlert);
            } catch (Exception e) {
                handleConnectionError(e);
                e.printStackTrace();
                Platform.runLater(() -> showErrorAlert("Send Error", "Failed to send email: " + e.getMessage()));
            }
        });
    }


    private synchronized void fetchNewEmails() {
        if (!isConnected()) {
            return;  // Silently return if not connected
        }

        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
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
            } catch (ConnectException e) {
                // Silently handle disconnection
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
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Conferma eliminazione");
            confirmAlert.setHeaderText("Sei sicuro di voler eliminare questa email?");
            confirmAlert.setContentText("Questa azione non può essere annullata.");

            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                deleteEmail(selectedEmail);
            }
        } else {
            showErrorAlert("Nessuna email selezionata", "Seleziona un'email da eliminare.");
        }
    }

    private void deleteEmail(Email email) {
        if (!isConnected()) {
            showServerClosedAlert();
            return;
        }

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "DELETE_EMAIL");
            NetworkUtils.sendObject(socket, email.getId());
            String response = (String) NetworkUtils.receiveObject(socket);
            if ("SUCCESS".equals(response)) {
                Platform.runLater(() -> {
                    mailbox.removeEmail(email);
                    refreshEmailTable();

                    // Delete the email from the file system
                    try {
                        EmailFileManager.deleteEmail(String.valueOf(email.getId()), mailbox.getEmailAddress());
                        showInfoAlert("Email eliminata", "L'email è stata eliminata con successo dal server e dal file system.");
                    } catch (IOException e) {
                        showErrorAlert("Errore di eliminazione", "L'email è stata eliminata dal server, ma non è stato possibile eliminarla dal file system: " + e.getMessage());
                    }

                    returnToEmailListView();
                });
            } else {
                Platform.runLater(() -> showErrorAlert("Errore di eliminazione", "Impossibile eliminare l'email dal server."));
            }
        } catch (ConnectException e) {
            Platform.runLater(this::showServerClosedAlert);
        } catch (Exception e) {
            handleConnectionError(e);
            Platform.runLater(() -> showErrorAlert("Errore di eliminazione", "Impossibile eliminare l'email: " + e.getMessage()));
        }
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