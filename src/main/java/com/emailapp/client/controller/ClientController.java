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

    private Set<String> validEmails;
    private final Object lock = new Object();
    private boolean isComposeViewVisible = false;
    private Set<Integer> deletedEmailIds = new HashSet<>();

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
        // Reset dello stato e pulizia dei campi
        clearComposeFields();
        isComposeViewVisible = true;

        // Assicurarsi che la view di composizione sia nel posto giusto
        if (!detailOrComposeStack.getChildren().contains(composeView)) {
            detailOrComposeStack.getChildren().add(composeView);
        }

        // Mostrare la sezione di composizione
        composeView.setVisible(true);
        emailDetailFlow.setVisible(false);
        emailTableView.setVisible(false);
        actionButtons.setVisible(false);

        // Impostare le dimensioni della sezione di composizione
        composeView.prefWidthProperty().bind(detailOrComposeStack.widthProperty());
        composeView.prefHeightProperty().bind(detailOrComposeStack.heightProperty());

        // Ridefinire l'azione del pulsante di invio
        sendButton.setOnAction(event -> handleSendEmail());
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
    private void handleBackButton() {
        // Reset dello stato
        isComposeViewVisible = false;

        // Slegare i binding delle dimensioni
        composeView.prefWidthProperty().unbind();
        composeView.prefHeightProperty().unbind();

        // Nascondere tutte le viste secondarie
        composeView.setVisible(false);
        emailDetailFlow.setVisible(false);
        actionButtons.setVisible(false);

        // Mostrare la tabella delle email
        emailTableView.setVisible(true);

        // Pulire il contenitore dei dettagli
        detailOrComposeStack.getChildren().clear();

        // Aggiornare la tabella
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
                // Non facciamo nulla dopo l'alert, lasciando tutto com'è
                return;
            }

            // Solo se arriviamo qui (tutto è valido) procediamo con l'invio
            Email newEmail = new Email();
            newEmail.setSender(mailbox.getEmailAddress());
            newEmail.setRecipients(recipients);
            newEmail.setSubject(subjectField.getText());
            newEmail.setBody(bodyArea.getText());

            synchronized (lock) {
                sendEmail(newEmail);
            }
            returnToEmailListView();
        }
    }


    private boolean isValidRecipient(String email) {
        return validEmails.contains(email.trim());
}


    private void clearComposeFields() {
        // Pulire tutti i campi di input
        if (toField != null) toField.clear();
        if (subjectField != null) subjectField.clear();
        if (bodyArea != null) bodyArea.clear();
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

    private synchronized boolean sendEmail(Email email) {
        Socket socket = null;
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            NetworkUtils.sendObject(socket, "SEND_EMAIL");
            NetworkUtils.sendObject(socket, email);
            String response = (String) NetworkUtils.receiveObject(socket);

            if ("OK".equals(response)) {
                Platform.runLater(() -> {
                    mailbox.addSentEmail(email);
                    refreshEmailTable();
                    showInfoAlert("Email Inviata", "Email inviata correttamente a " + email.getRecipients());
                });
                return true;
            } else {
                Platform.runLater(() -> {
                    showErrorAlert("Errore di Invio", "Impossibile inviare l'email: " + response);
                });
                return false;
            }
        } catch (Exception e) {
            handleConnectionError(e);
            return false;
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    System.err.println("Errore chiusura socket: " + ex.getMessage());
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
                        // Skip emails that were previously deleted
                        if (!deletedEmailIds.contains(email.getId()) &&
                                !mailbox.hasEmail(email.getId())) {
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
            Alert confirmDelete = new Alert(Alert.AlertType.CONFIRMATION);
            confirmDelete.setTitle("Conferma eliminazione");
            confirmDelete.setHeaderText("Eliminare questa email?");
            confirmDelete.setContentText("Questa operazione non può essere annullata.");

            Optional<ButtonType> result = confirmDelete.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                deleteEmail(selectedEmail);
            }
        }
    }

    private void deleteEmail(Email email) {
        if (deleteEmailFromServer(email)) {
            mailbox.removeEmail(email);
            deletedEmailIds.add(email.getId());
            refreshEmailTable();
            returnToEmailListView();
            showInfoAlert("Email eliminata", "L'email è stata eliminata con successo.");
        } else {
            showErrorAlert("Errore", "Impossibile eliminare l'email.");
        }
    }

    private boolean deleteEmailFromServer(Email email) {
        Socket socket = null;
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            NetworkUtils.sendObject(socket, "DELETE_EMAIL");
            NetworkUtils.sendObject(socket, email.getId());
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

            String response = (String) NetworkUtils.receiveObject(socket);
            return "OK".equals(response);
        } catch (Exception e) {
            handleConnectionError(e);
            return false;
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


    private void openComposeWindow(Email selectedEmail, String mode) {
        // Show composition view
        composeView.setVisible(true);
        emailDetailFlow.setVisible(false);
        emailTableView.setVisible(false);
        actionButtons.setVisible(false);

        // Pre-fill fields based on mode
        switch (mode) {
            case "Reply":
                toField.setText(selectedEmail.getSender());
                subjectField.setText("Re: " + selectedEmail.getSubject());
                bodyArea.setText("\n\n----- Original Message -----\n" + selectedEmail.getBody());
                break;

            case "Reply All":
                // Get all recipients except current user
                List<String> allRecipients = new ArrayList<>(selectedEmail.getRecipients());
                allRecipients.add(selectedEmail.getSender());
                allRecipients.remove(mailbox.getEmailAddress());
                toField.setText(String.join(", ", allRecipients));
                subjectField.setText("Re: " + selectedEmail.getSubject());
                bodyArea.setText("\n\n----- Original Message -----\n" + selectedEmail.getBody());
                break;

            case "Forward":
                toField.setText("");
                subjectField.setText("Fwd: " + selectedEmail.getSubject());
                String forwardedContent = "\n\n----- Forwarded Message -----\n" +
                        "From: " + selectedEmail.getSender() + "\n" +
                        "Date: " + selectedEmail.getSentDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n" +
                        "Subject: " + selectedEmail.getSubject() + "\n" +
                        "To: " + String.join(", ", selectedEmail.getRecipients()) + "\n\n" +
                        selectedEmail.getBody();
                bodyArea.setText(forwardedContent);
                break;
        }

        // Set up send button action
        sendButton.setOnAction(event -> handleSendEmail());

        // Configure layout
        emailTableView.setVisible(true);
        detailOrComposeStack.getChildren().setAll(composeView);
        composeView.prefWidthProperty().bind(detailOrComposeStack.widthProperty());
        composeView.prefHeightProperty().bind(detailOrComposeStack.heightProperty());
    }

    @FXML
    private void handleBackInCompose() {
        // Verificare la visibilità della sezione di composizione
        if (isComposeViewVisible) {
            // Nascondere la sezione di composizione
            composeView.setVisible(false);
            isComposeViewVisible = false;
        }

        // Mostrare nuovamente la tabella delle email
        emailTableView.setVisible(true);

        // Pulire il pannello a destra
        detailOrComposeStack.getChildren().clear();

        // Aggiornare la tabella delle email
        refreshEmailTable();
    }

    @FXML
    private void returnToEmailListView() {
        // Reset dello stato
        isComposeViewVisible = false;

        // Slegare i binding delle dimensioni
        composeView.prefWidthProperty().unbind();
        composeView.prefHeightProperty().unbind();

        // Nascondere le viste secondarie
        composeView.setVisible(false);
        emailDetailFlow.setVisible(false);
        actionButtons.setVisible(false);

        // Mostrare la tabella delle email
        emailTableView.setVisible(true);

        // Pulire il contenitore dei dettagli
        detailOrComposeStack.getChildren().clear();

        // Reimpostare il layout
        emailTableView.setManaged(true);
        emailTableView.setMaxWidth(Double.MAX_VALUE);
        emailTableView.setMaxHeight(Double.MAX_VALUE);

        // Aggiornare la vista
        refreshEmailTable();
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