package com.emailapp.client.model;

import com.emailapp.util.Email;
import com.emailapp.util.NetworkUtils;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import java.nio.file.Path;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;

public class MailClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final int RETRY_DELAY = 5000;

    private final Mailbox mailbox;
    private final BooleanProperty connectedProperty;
    private volatile boolean isShuttingDown = false;
    private final List<EmailUpdateListener> listeners = new ArrayList<>();
    private final ScheduledExecutorService connectionChecker;
    private final ScheduledExecutorService executorService;
    public static final String RECEIVED_EMAILS = "Email ricevute";
    public static final String SENT_EMAILS = "Email inviate";

    public MailClient(String emailAddress) {
        if (emailAddress.contains(",")) {
            emailAddress = emailAddress.split(",")[0].trim();
        }
        this.mailbox = new Mailbox(emailAddress.split(",")[0].trim());
        this.executorService = Executors.newScheduledThreadPool(2);
        this.connectionChecker = Executors.newSingleThreadScheduledExecutor();
        this.connectedProperty = new SimpleBooleanProperty(false);
        startConnectionChecker();
        startEmailPolling();  // Add this line
    }

    private void startConnectionChecker() {
        connectionChecker.scheduleAtFixedRate(() -> {
            if (!isShuttingDown) {
                Platform.runLater(this::checkConnection);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void startEmailPolling() {
        executorService.scheduleAtFixedRate(
                this::pollForNewEmails,
                0,
                5,
                TimeUnit.SECONDS
        );
    }

    public void addEmailUpdateListener(EmailUpdateListener listener) {
        listeners.add(listener);
    }

    public void removeEmailUpdateListener(EmailUpdateListener listener) {
        listeners.remove(listener);
    }

    public void filterEmails(String filter) {
        executorService.submit(() -> {
            try {
                List<Email> emails;
                if (isConnected()) {
                    emails = fetchEmails(filter);
                } else {
                    emails = filter.equals(SENT_EMAILS) ?
                            mailbox.getSentEmails() :
                            mailbox.getReceivedEmails();
                }

                final List<Email> finalEmails = new ArrayList<>(emails);
                Platform.runLater(() -> {
                    for (EmailUpdateListener listener : listeners) {
                        listener.onEmailsFiltered(filter, finalEmails);
                    }
                });
            } catch (Exception e) {
                handleError(e);
            }
        });
    }

    private void reconnect() {
        executorService.schedule(() -> {
            if (!isConnected() && !isShuttingDown) {
                checkConnection();
                if (isConnected()) {
                    pollForNewEmails();
                }
            }
        }, 5, TimeUnit.SECONDS);
    }

    public void pollForNewEmails() {
        if (!isConnected() || isShuttingDown) return;

        executorService.submit(() -> {
            try {
                List<Email> newEmails = checkNewEmails();
                if (newEmails != null && !newEmails.isEmpty()) {
                    Platform.runLater(() -> {
                        // Add only new emails to the existing list
                        newEmails.forEach(mailbox::addReceivedEmail);
                        for (EmailUpdateListener listener : listeners) {
                            listener.onNewEmailsReceived(newEmails);
                        }
                    });
                }
            } catch (Exception e) {
                handleError(e);
            }
        });
    }

    private void handleError(Exception e) {
        Platform.runLater(() -> {
            for (EmailUpdateListener listener : listeners) {
                listener.onEmailUpdateError(e);
            }
        });
    }

    public BooleanProperty connectedProperty() {
        return connectedProperty;
    }

    public boolean isConnected() {
        return connectedProperty.get();
    }

    public void checkConnection() {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "PING");
            String response = (String) NetworkUtils.receiveObject(socket);
            connectedProperty.set("PONG".equals(response));
        } catch (Exception e) {
            connectedProperty.set(false);
            reconnect();
        }
    }

    public List<Email> fetchEmails(String filter) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            String command = filter.equals(SENT_EMAILS) ? "FETCH_SENT_EMAILS" : "FETCH_RECEIVED_EMAILS";
            NetworkUtils.sendObject(socket, command);
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

            Object response = NetworkUtils.receiveObject(socket);
            if (response instanceof List<?>) {
                List<Email> emails = (List<Email>) response;
                // Maintain read status for each email
                emails.forEach(email -> {
                    if (isEmailRead(String.valueOf(email.getId()))) {
                        email.setRead(true);
                    }
                });
                return emails;
            }
            return new ArrayList<>();
        }
    }



    public void markEmailAsRead(Email email) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "MARK_AS_READ");
            NetworkUtils.sendObject(socket, email.getId());
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

            String response = (String) NetworkUtils.receiveObject(socket);
            if (!"OK".equals(response)) {
                throw new IOException("Failed to mark email as read");
            }
            email.setRead(true);
        }
    }

    public String sendEmail(Email email) {
        try {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "SEND_EMAIL");
                NetworkUtils.sendObject(socket, email);
                NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

                Object response = NetworkUtils.receiveObject(socket);
                socket.close();
                return response != null ? response.toString() : "Email inviata con successo";
            }
        } catch (Exception e) {
            return "Errore: " + e.getMessage();
        }
    }

    public void handleEmailFiltering(String filter) {
        executorService.submit(() -> {
            try {
                if (!isConnected()) {
                    handleError(new Exception("Server non raggiungibile"));
                    return;
                }

                List<Email> emails = fetchEmails(filter);
                if (emails != null) {
                    Platform.runLater(() -> {
                        mailbox.clearEmails();
                        if (filter.equals(SENT_EMAILS)) {
                            emails.forEach(mailbox::addSentEmail);
                        } else {
                            emails.forEach(mailbox::addReceivedEmail);
                        }
                        notifyEmailsFiltered(filter, emails);
                    });
                }
            } catch (Exception e) {
                handleError(e);
            }
        });
    }


    public void handleEmailRead(Email email) {
        if (!email.isRead()) {
            try {
                markEmailAsRead(email);  // Server-side update
                email.setRead(true);     // Local update
                mailbox.updateEmailReadStatus(email);

                // Persistent storage
                String emailId = String.valueOf(email.getId());
                String userEmail = mailbox.getEmailAddress();
                Path readStatusFile = Paths.get("data", userEmail, "read_emails.txt");
                Files.createDirectories(readStatusFile.getParent());
                Files.write(readStatusFile,
                        Collections.singletonList(emailId),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);

                notifyEmailMarkedAsRead(email);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isEmailRead(String emailId) {
        try {
            Path readStatusFile = Paths.get("data", mailbox.getEmailAddress(), "read_emails.txt");
            if (Files.exists(readStatusFile)) {
                return Files.lines(readStatusFile)
                        .anyMatch(line -> line.trim().equals(emailId));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }


    public String handleEmailSend(String[] recipients, String subject, String body) throws Exception {
        if (recipients == null || recipients.length == 0) {
            return "Nessun destinatario specificato";
        }

        try {
            Email newEmail = createEmail(
                    mailbox.getEmailAddress(),
                    Arrays.asList(recipients),
                    subject,
                    body
            );
            return sendEmail(newEmail);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Errore durante l'invio: " + e.getMessage());
        }
    }


    private void notifyEmailsFiltered(String filter, List<Email> emails) {
        for (EmailUpdateListener listener : listeners) {
            listener.onEmailsFiltered(filter, emails);
        }
    }

    private void notifyEmailMarkedAsRead(Email email) {
        for (EmailUpdateListener listener : listeners) {
            listener.onEmailMarkedAsRead(email);
        }
    }



    public Email createActionEmail(String action, int emailId) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, action); // "REPLY" or "REPLY_ALL" or "FORWARD"
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());
            NetworkUtils.sendObject(socket, emailId);

            String response = (String) NetworkUtils.receiveObject(socket);
            if ("OK".equals(response)) {
                Email template = (Email) NetworkUtils.receiveObject(socket);
                template.setSender(mailbox.getEmailAddress());
                return template;
            }
            throw new IOException("Failed to create reply email");
        }
    }

    public Email createEmail(String sender, List<String> recipients, String subject, String body) {
        Email email = new Email();
        email.setSender(sender);
        email.setRecipients(recipients);
        email.setSubject(subject.trim());
        email.setBody(body.trim());
        email.setSentDate(LocalDateTime.now());
        return email;
    }

    public void deleteEmail(String emailId) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "DELETE_EMAIL");
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());
            NetworkUtils.sendObject(socket, emailId);

            String response = (String) NetworkUtils.receiveObject(socket);
            if (!"OK".equals(response)) {
                throw new IOException("Failed to delete email");
            }
        }
    }

    public List<Email> checkNewEmails() throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "CHECK_NEW_EMAILS");
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

            Object response = NetworkUtils.receiveObject(socket);
            if (response instanceof List<?>) {
                List<?> list = (List<?>) response;
                if (!list.isEmpty() && list.get(0) instanceof Email) {
                    List<Email> newEmails = (List<Email>) list;
                    // Preserve read status for existing emails
                    newEmails.forEach(email -> {
                        if (isEmailRead(String.valueOf(email.getId()))) {
                            email.setRead(true);
                        }
                    });
                    return newEmails;
                }
            }
            return List.of();
        }
    }

    public Mailbox getMailbox() {
        return mailbox;
    }

    public void shutdown() {
        isShuttingDown = true;
        try {
            connectionChecker.shutdown();
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}