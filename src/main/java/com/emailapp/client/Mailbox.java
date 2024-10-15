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
                System.out.println("Loading emails for: " + emailAddress);
                List<Email> loadedEmails = EmailFileManager.loadEmails(emailAddress);
                System.out.println("Loaded " + loadedEmails.size() + " emails");
                Platform.runLater(() -> {
                    synchronized (lock) {
                        clearAllEmails();
                        for (Email email : loadedEmails) {
                            addReceivedEmail(email);
                        }
                        System.out.println("Total emails after loading: " + getTotalEmailCount());
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

    public synchronized void fetchNewEmails() {
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "FETCH_NEW_EMAILS");
                NetworkUtils.sendObject(socket, emailAddress);
                @SuppressWarnings("unchecked")
                List<Email> newEmails = (List<Email>) NetworkUtils.receiveObject(socket);
                Platform.runLater(() -> {
                    synchronized (lock) {
                        for (Email email : newEmails) {
                            if (!hasEmail(email.getId())) {
                                addReceivedEmail(email);
                            }
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
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

    public synchronized void addReceivedEmail(Email email) {
        if (!hasEmail(email.getId())) {
            receivedEmails.add(email);
        }
    }

    public void clearEmails() {
        receivedEmails.clear();
        sentEmails.clear();
    }

    public synchronized void addSentEmail(Email email) {
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

    public synchronized void removeEmail(Email email) {
        receivedEmails.removeIf(e -> e.getId().equals(email.getId()));
        sentEmails.removeIf(e -> e.getId().equals(email.getId()));
    }



    public synchronized boolean hasEmail(String emailId) {
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

    public synchronized void deleteEmail(Email email) {
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "DELETE_EMAIL");
                NetworkUtils.sendObject(socket, email.getId());
                String response = (String) NetworkUtils.receiveObject(socket);
                if ("SUCCESS".equals(response)) {
                    Platform.runLater(() -> {
                        synchronized (lock) {
                            removeEmail(email);
                        }
                    });
                    EmailFileManager.deleteEmail(email.getId(), emailAddress);
                }
            } catch (Exception e) {
                e.printStackTrace();
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
