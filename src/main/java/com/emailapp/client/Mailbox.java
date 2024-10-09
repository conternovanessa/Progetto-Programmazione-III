package com.emailapp.client;

import com.emailapp.EmailFileManager;
import com.emailapp.NetworkUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Mailbox {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    private String emailAddress;
    private ObservableList<Email> receivedEmails;
    private ObservableList<Email> sentEmails;
    private ExecutorService executorService;

    public Mailbox(String emailAddress) {
        this.emailAddress = emailAddress;
        this.receivedEmails = FXCollections.observableArrayList();
        this.sentEmails = FXCollections.observableArrayList();
        this.executorService = Executors.newCachedThreadPool();
    }

    public void loadEmailsFromDisk() {
        executorService.submit(() -> {
            try {
                List<Email> loadedEmails = EmailFileManager.loadEmails(emailAddress);
                Platform.runLater(() -> {
                    for (Email email : loadedEmails) {
                        addReceivedEmail(email);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                // Note: Error handling should be done in the UI layer
            }
        });
    }


    public void setEmailAddress(String emailAddress) {
        if (!this.emailAddress.equals(emailAddress)) {
            this.emailAddress = emailAddress;
            clearAllEmails();
            loadEmailsFromDisk();
        }
    }


    public void sendEmail(Email email) {
        if (email.isEmpty()) {
            // Note: Error handling should be done in the UI layer
            return;
        }

        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                EmailFileManager.saveEmail(email, emailAddress);
                NetworkUtils.sendObject(socket, "SEND_EMAIL");
                NetworkUtils.sendObject(socket, email);
                String response = (String) NetworkUtils.receiveObject(socket);
                if ("SUCCESS".equals(response)) {
                    Platform.runLater(() -> addSentEmail(email));
                } else {
                    // Note: Error handling should be done in the UI layer
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Note: Error handling should be done in the UI layer
            }
        });
    }
    public void fetchNewEmails() {
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "FETCH_NEW_EMAILS");
                NetworkUtils.sendObject(socket, emailAddress);
                @SuppressWarnings("unchecked")
                List<Email> newEmails = (List<Email>) NetworkUtils.receiveObject(socket);
                Platform.runLater(() -> {
                    for (Email email : newEmails) {
                        if (!hasEmail(email.getId())) {
                            addReceivedEmail(email);
                        }
                    }
                });
            } catch (Exception e) {
                // Note: Error handling should be done in the UI layer
            }
        });
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public ObservableList<Email> getReceivedEmails() {
        return receivedEmails;
    }

    public ObservableList<Email> getSentEmails() {
        return sentEmails;
    }

    public void addReceivedEmail(Email email) {
        if (!hasEmail(email.getId())) {
            receivedEmails.add(email);
        }
    }

    public void addSentEmail(Email email) {
        if (!hasEmail(email.getId())) {
            sentEmails.add(email);
        }
    }

    public void removeReceivedEmail(Email email) {
        receivedEmails.removeIf(e -> e.getId().equals(email.getId()));
    }

    public void removeSentEmail(Email email) {
        sentEmails.removeIf(e -> e.getId().equals(email.getId()));
    }

    public ObservableList<Email> getAllEmails() {
        ObservableList<Email> allEmails = FXCollections.observableArrayList();
        allEmails.addAll(receivedEmails);
        allEmails.addAll(sentEmails);
        return allEmails;
    }

    public int getTotalEmailCount() {
        return receivedEmails.size() + sentEmails.size();
    }

    public void removeEmail(Email email) {
        removeReceivedEmail(email);
        removeSentEmail(email);
    }

    public boolean hasEmail(String emailId) {
        return receivedEmails.stream().anyMatch(e -> e.getId().equals(emailId)) ||
                sentEmails.stream().anyMatch(e -> e.getId().equals(emailId));
    }

    public Email getEmailById(String emailId) {
        return getAllEmails().stream()
                .filter(e -> e.getId().equals(emailId))
                .findFirst()
                .orElse(null);
    }


    public void clearAllEmails() {
        receivedEmails.clear();
        sentEmails.clear();
    }

    public void deleteEmail(Email email) {
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "DELETE_EMAIL");
                NetworkUtils.sendObject(socket, email.getId());
                String response = (String) NetworkUtils.receiveObject(socket);
                if ("SUCCESS".equals(response)) {
                    Platform.runLater(() -> removeEmail(email));
                    EmailFileManager.deleteEmail(email.getId(), emailAddress);
                } else {
                    // Note: Error handling should be done in the UI layer
                }
            } catch (Exception e) {
                // Note: Error handling should be done in the UI layer
            }
        });
    }

    @Override
    public String toString() {
        return "Mailbox{" +
                "emailAddress='" + emailAddress + '\'' +
                ", receivedEmails=" + receivedEmails.size() +
                ", sentEmails=" + sentEmails.size() +
                '}';
    }
}