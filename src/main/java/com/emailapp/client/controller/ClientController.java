package com.emailapp.client.controller;

import com.emailapp.client.model.Email;
import com.emailapp.client.model.EmailDraft;
import com.emailapp.client.model.Mailbox;
import com.emailapp.common.EmailPersistence;
import com.emailapp.common.NetworkUtils;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.control.Alert;

import java.io.IOException;
import java.net.Socket;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientController {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    private final Mailbox mailbox;
    private static ExecutorService executorService = null;
    private final BooleanProperty connectedProperty;
    private final ObservableMap<String, EmailDraft> activeDrafts;

    public ClientController(String emailAddress) {
        this.mailbox = new Mailbox(emailAddress);
        this.executorService = Executors.newCachedThreadPool();
        this.connectedProperty = new SimpleBooleanProperty(false);
        this.activeDrafts = FXCollections.observableHashMap();
        startConnectionChecker();
        loadEmails();
    }

    public EmailDraft startNewDraft() {
        EmailDraft draft = new EmailDraft(mailbox.getEmailAddress());
        activeDrafts.put(draft.getId(), draft);
        notifyServerAboutDraft(draft);
        return draft;
    }

    public EmailDraft createReplyDraft(Email originalEmail) {
        EmailDraft draft = new EmailDraft(mailbox.getEmailAddress());
        draft.setRecipients(originalEmail.getSender());
        draft.setSubject("Re: " + originalEmail.getSubject());
        draft.setBody("\n\nOn " + originalEmail.getSentDate() + ", " + originalEmail.getSender() + " wrote:\n" + originalEmail.getBody());
        activeDrafts.put(draft.getId(), draft);
        notifyServerAboutDraft(draft);
        return draft;
    }

    public EmailDraft createReplyAllDraft(Email originalEmail) {
        EmailDraft draft = new EmailDraft(mailbox.getEmailAddress());
        List<String> recipients = originalEmail.getRecipients();
        recipients.remove(mailbox.getEmailAddress());
        recipients.add(originalEmail.getSender());
        draft.setRecipients(String.join(",", recipients));
        draft.setSubject("Re: " + originalEmail.getSubject());
        draft.setBody("\n\nOn " + originalEmail.getSentDate() + ", " + originalEmail.getSender() + " wrote:\n" + originalEmail.getBody());
        activeDrafts.put(draft.getId(), draft);
        notifyServerAboutDraft(draft);
        return draft;
    }

    public EmailDraft createForwardDraft(Email originalEmail) {
        EmailDraft draft = new EmailDraft(mailbox.getEmailAddress());
        draft.setSubject("Fwd: " + originalEmail.getSubject());
        draft.setBody("\n\n---------- Forwarded message ---------\n" +
                "From: " + originalEmail.getSender() + "\n" +
                "Date: " + originalEmail.getSentDate() + "\n" +
                "Subject: " + originalEmail.getSubject() + "\n" +
                "To: " + String.join(", ", originalEmail.getRecipients()) + "\n\n" +
                originalEmail.getBody());
        activeDrafts.put(draft.getId(), draft);
        notifyServerAboutDraft(draft);
        return draft;
    }

    public ObservableList<EmailDraft> getActiveDrafts() {
        return FXCollections.observableArrayList(activeDrafts.values());
    }

    private void notifyServerAboutDraft(EmailDraft draft) {
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "START_DRAFT");
                NetworkUtils.sendObject(socket, draft);
            } catch (Exception e) {
                handleConnectionError(e);
            }
        });
    }

    public void updateDraft(EmailDraft draft) {
        notifyServerAboutDraft(draft);
    }

    public void sendDraft(EmailDraft draft) {
        Email email = new Email(draft.getSender(),
                Arrays.asList(draft.getRecipients().split(",")),
                draft.getSubject(),
                draft.getBody());
        sendEmail(email);
        activeDrafts.remove(draft.getId());
        notifyServerAboutDraftCompletion(draft.getId());
    }

    private void notifyServerAboutDraftCompletion(String draftId) {
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "COMPLETE_DRAFT");
                NetworkUtils.sendObject(socket, draftId);
            } catch (Exception e) {
                handleConnectionError(e);
            }
        });
    }

    public void fetchActiveDrafts() {
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "FETCH_ACTIVE_DRAFTS");
                @SuppressWarnings("unchecked")
                Map<String, EmailDraft> serverDrafts = (Map<String, EmailDraft>) NetworkUtils.receiveObject(socket);
                Platform.runLater(() -> {
                    activeDrafts.clear();
                    activeDrafts.putAll(serverDrafts);
                });
            } catch (Exception e) {
                handleConnectionError(e);
            }
        });
    }

    public void sendEmail(Email email) {
        if (!isConnected()) {
            showServerClosedAlert();
            return;
        }

        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "SEND_EMAIL");
                NetworkUtils.sendObject(socket, email);
                String response = (String) NetworkUtils.receiveObject(socket);
                if ("SUCCESS".equals(response)) {
                    Platform.runLater(() -> mailbox.addSentEmail(email));
                    try {
                        EmailPersistence.saveEmail(email);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Platform.runLater(() -> showErrorAlert("Send Error", "Failed to send email"));
                }
            } catch (ConnectException e) {
                Platform.runLater(this::showServerClosedAlert);
            } catch (Exception e) {
                handleConnectionError(e);
            }
        });
    }

    public void fetchNewEmails() {
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
                    for (Email email : newEmails) {
                        mailbox.addReceivedEmail(email);
                        try {
                            EmailPersistence.saveEmail(email);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    showInfoAlert("New Emails", "Received " + newEmails.size() + " new email(s)");
                });
            } catch (ConnectException e) {
                Platform.runLater(this::showServerClosedAlert);
            } catch (Exception e) {
                handleConnectionError(e);
            }
        });
    }

    public void deleteEmail(Email email) {
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
                } else {
                    Platform.runLater(() -> showErrorAlert("Delete Error", "Failed to delete email"));
                }
            } catch (ConnectException e) {
                Platform.runLater(this::showServerClosedAlert);
            } catch (Exception e) {
                handleConnectionError(e);
            }
        });
    }

    public void checkConnection() {
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "PING");
                String response = (String) NetworkUtils.receiveObject(socket);
                boolean isConnected = "PONG".equals(response);
                Platform.runLater(() -> connectedProperty.set(isConnected));
            } catch (Exception e) {
                Platform.runLater(() -> connectedProperty.set(false));
            }
        });
    }

    public ObservableList<Email> getEmails() {
        return mailbox.getAllEmails();
    }

    public String getEmailAddress() {
        return mailbox.getEmailAddress();
    }

    public BooleanProperty connectedProperty() {
        return connectedProperty;
    }

    public boolean isConnected() {
        return connectedProperty.get();
    }

    private void handleConnectionError(Exception e) {
        Platform.runLater(() -> {
            connectedProperty.set(false);
            showErrorAlert("Connection Error", "Failed to connect to the server: " + e.getMessage());
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

    public void loadEmails() {
        try {
            List<Email> loadedEmails = EmailPersistence.loadAllEmails();
            Platform.runLater(() -> {
                for (Email email : loadedEmails) {
                    if (email.getSender().equals(mailbox.getEmailAddress())) {
                        mailbox.addSentEmail(email);
                    } else {
                        mailbox.addReceivedEmail(email);
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void shutdown() {
        // This method should be called when the application is closing
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}