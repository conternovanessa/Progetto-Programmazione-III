package com.emailapp.client.model;

import com.emailapp.util.Email;
import com.emailapp.util.NetworkUtils;
import com.emailapp.util.EmailFileManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
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
    private Runnable emailLoadedCallback;
    private final Object lock = new Object();

    public Mailbox(String emailAddress) {
        this.emailAddress = emailAddress;
        this.receivedEmails = FXCollections.observableArrayList();
        this.sentEmails = FXCollections.observableArrayList();
        this.executorService = Executors.newCachedThreadPool();
    }


    public synchronized void loadEmailsFromDisk() {
        executorService.submit(() -> {
            try {
                List<Email> loadedEmails = EmailFileManager.loadEmails(emailAddress);
                Platform.runLater(() -> {
                    synchronized (lock) {
                        clearAllEmails();
                        for (Email email : loadedEmails) {
                            if (email.getSender().equals(emailAddress)) {
                                sentEmails.add(email);
                            } else {
                                receivedEmails.add(email);
                            }
                        }
                        if (emailLoadedCallback != null) {
                            emailLoadedCallback.run();
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Errore nel caricamento dei file:" + e.getMessage());
            }
        });
    }

    public synchronized void addNewEmail(Email email) {
        Platform.runLater(() -> {
            synchronized (lock) {
                ObservableList<Email> targetList = email.getSender().equals(emailAddress) ?
                        sentEmails : receivedEmails;

                // Verifica se l'email esiste già
                boolean exists = targetList.stream()
                        .anyMatch(existing -> existing.getId() == email.getId());

                if (!exists) {
                    // Preserva lo stato di lettura se l'email esiste
                    targetList.stream()
                            .filter(existing -> existing.getId() == email.getId())
                            .findFirst()
                            .ifPresent(existing -> email.setRead(existing.isRead()));

                    targetList.add(0, email);
                }
            }
        });
    }

    public void setEmailAddress(String emailAddress) {
        // Rimuovi la porta se presente nell'indirizzo email
        if (emailAddress.contains(",")) {
            emailAddress = emailAddress.split(",")[0].trim();
        }

        if (!this.emailAddress.equals(emailAddress)) {
            this.emailAddress = emailAddress;
            clearAllEmails();
            loadEmailsFromDisk();
        }
    }

    public void updateEmailReadStatus(Email email) {
        Platform.runLater(() -> {
            synchronized (lock) {
                // Update in received emails list
                receivedEmails.stream()
                        .filter(e -> e.getId() == email.getId())
                        .findFirst()
                        .ifPresent(e -> {
                            e.setRead(true);
                            int index = receivedEmails.indexOf(e);
                            receivedEmails.set(index, e); // Force update
                        });

                // Update in sent emails list
                sentEmails.stream()
                        .filter(e -> e.getId() == email.getId())
                        .findFirst()
                        .ifPresent(e -> {
                            e.setRead(true);
                            int index = sentEmails.indexOf(e);
                            sentEmails.set(index, e); // Force update
                        });
            }
        });
    }

    public void setEmailLoadedCallback(Runnable callback) {
        this.emailLoadedCallback = callback;
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
        receivedEmails.add(email);
    }

    public void addSentEmail(Email email) {
        sentEmails.add(email);
    }

    public void clearEmails() {
        receivedEmails.clear();
        sentEmails.clear();
    }


    public int getTotalEmailCount() {
        return receivedEmails.size() + sentEmails.size();
    }

    public synchronized void removeEmail(Email email) {
        receivedEmails.removeIf(e -> e.getId() == email.getId());
        sentEmails.removeIf(e -> e.getId() == email.getId());
    }

    public synchronized boolean hasEmail(int emailId) {
        return receivedEmails.stream().anyMatch(e -> e.getId() == emailId) ||
                sentEmails.stream().anyMatch(e -> e.getId() == emailId);
    }

    public void clearAllEmails() {
        receivedEmails.clear();
        sentEmails.clear();
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
