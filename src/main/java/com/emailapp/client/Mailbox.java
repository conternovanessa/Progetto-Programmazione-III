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
                            addReceivedEmail(email);
                        }
                        if (emailLoadedCallback != null) {
                            emailLoadedCallback.run();
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Error loading emails: " + e.getMessage());
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

    public void setEmailLoadedCallback(Runnable callback) {
        this.emailLoadedCallback = callback;
    }


    public synchronized void sendEmail(Email email) {
        if (email.isEmpty()) {
            return;
        }

        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                EmailFileManager.saveEmail(email, emailAddress);
                NetworkUtils.sendObject(socket, "SEND_EMAIL");
                NetworkUtils.sendObject(socket, email);
                String response = (String) NetworkUtils.receiveObject(socket);
                if ("SUCCESS".equals(response)) {
                    Platform.runLater(() -> {
                        synchronized (lock) {
                            addSentEmail(email);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
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

    public synchronized void addReceivedEmail(Email email) {
        if (!hasEmail(Long.parseLong(String.valueOf(email.getId())))) {
            receivedEmails.add(email);
        }
    }

    public void clearEmails() {
        receivedEmails.clear();
        sentEmails.clear();
    }

    public synchronized void addSentEmail(Email email) {
        if (!hasEmail(Long.parseLong(String.valueOf(email.getId())))) {
            sentEmails.add(email);
        }
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

    public synchronized void removeEmail(Email email) {
        receivedEmails.removeIf(e -> e.getId() == email.getId());
        sentEmails.removeIf(e -> e.getId() == email.getId());
    }



    public synchronized boolean hasEmail(long emailId) {
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
