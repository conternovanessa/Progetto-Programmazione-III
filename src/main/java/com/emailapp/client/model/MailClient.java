package com.emailapp.client.model;

import com.emailapp.util.Email;
import com.emailapp.util.EmailFileManager;
import com.emailapp.util.NetworkUtils;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.*;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Alert;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class MailClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    private final Mailbox mailbox;
    private final ExecutorService executorService;
    private final BooleanProperty connectedProperty;
    private volatile boolean isShuttingDown = false;
    private List<EmailUpdateListener> listeners = new ArrayList<>();
    public static final String RECEIVED_EMAILS = "Email ricevute";
    public static final String SENT_EMAILS = "Email inviate";

    private final Object listenersLock = new Object();
    private final Object connectionLock = new Object();
    private final ReentrantReadWriteLock mailboxLock = new ReentrantReadWriteLock(true);
    private static final long LOCK_TIMEOUT = 3000; // 3 secondi timeout
    private Socket clientSocket;

    public MailClient(String emailAddress) {
        // Rimuovi la porta se presente nell'indirizzo email
        if (emailAddress.contains(",")) {
            emailAddress = emailAddress.split(",")[0].trim();
        }
        this.mailbox = new Mailbox(emailAddress);
        this.executorService = Executors.newCachedThreadPool();
        this.connectedProperty = new SimpleBooleanProperty(false);
    }

    public void addEmailUpdateListener(EmailUpdateListener listener) {
        synchronized(listenersLock) {
            listeners.add(listener);
        }
    }

    public void removeEmailUpdateListener(EmailUpdateListener listener) {
        synchronized(listenersLock) {
            listeners.remove(listener);
        }
    }


    private void notifyListeners(Consumer<EmailUpdateListener> action) {
        synchronized(listenersLock) {
            for (EmailUpdateListener listener : listeners) {
                action.accept(listener);
            }
        }
    }

    public void filterEmails(String filter) throws Exception {
        try {
            mailboxLock.writeLock().tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            try {
                if (isConnected()) {
                    List<Email> emails = fetchEmails(filter);
                    if (emails != null) {
                        if (filter.equals(SENT_EMAILS)) {
                            // Aggiorna la mailbox solo con le nuove email inviate
                            for (Email email : emails) {
                                if (!mailbox.hasEmail(email.getId())) {
                                    mailbox.addSentEmail(email);
                                }
                            }
                        } else {
                            // Aggiorna la mailbox solo con le nuove email ricevute
                            for (Email email : emails) {
                                if (!mailbox.hasEmail(email.getId())) {
                                    mailbox.addReceivedEmail(email);
                                }
                            }
                        }
                        notifyListeners(listener -> listener.onEmailsFiltered(filter, emails));
                    }
                } else {
                    List<Email> localEmails = filter.equals(SENT_EMAILS) ?
                            new ArrayList<>(mailbox.getSentEmails()) :
                            new ArrayList<>(mailbox.getReceivedEmails());
                    notifyListeners(listener -> listener.onEmailsFiltered(filter, localEmails));
                }
            } finally {
                mailboxLock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Timeout durante l'accesso alla mailbox");
        }
    }

    public void pollForNewEmails() {
        if (!isConnected()) return;

        try {
            List<Email> newEmails = checkNewEmails();
            if (newEmails != null && !newEmails.isEmpty()) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Nuova Email");
                    alert.setHeaderText(null);
                    String message = newEmails.size() == 1
                            ? "Hai ricevuto 1 nuova mail"
                            : "Hai ricevuto " + newEmails.size() + " nuove mail";
                    alert.setContentText(message);
                    alert.show();
                });
                notifyListeners(listener -> listener.onNewEmailsReceived(newEmails));
            }
        } catch (Exception e) {
            notifyListeners(listener -> listener.onEmailUpdateError(e));
        }
    }


    public BooleanProperty connectedProperty() {
        return connectedProperty;
    }

    public boolean isConnected() {
        return connectedProperty.get();
    }

    public void checkConnection() {
        synchronized(connectionLock) {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "PING");
                String response = (String) NetworkUtils.receiveObject(socket);
                Platform.runLater(() -> connectedProperty.set("PONG".equals(response)));
            } catch (Exception e) {
                Platform.runLater(() -> connectedProperty.set(false));
            }
        }
    }

    public List<Email> fetchEmails(String filter) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            String command = filter.equals(SENT_EMAILS) ? "FETCH_SENT_EMAILS" : "FETCH_RECEIVED_EMAILS";
            NetworkUtils.sendObject(socket, command);
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

            Object response = NetworkUtils.receiveObject(socket);
            if (response instanceof List<?>) {
                return (List<Email>) response;
            }
            return new ArrayList<>();
        }
    }

    private int requestWriteSocket() throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "REQUEST_WRITE_SOCKET");
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());
            Object response = NetworkUtils.receiveObject(socket);
            return (int) response;
        }
    }

    public String sendEmail(Email email) throws IOException, ClassNotFoundException {
        int dedicatedPort = requestWriteSocket();
        if (dedicatedPort == -1) {
            throw new IOException("Failed to get dedicated port from server");
        }

        try (Socket dedicatedSocket = new Socket(SERVER_ADDRESS, dedicatedPort)) {
            NetworkUtils.sendObject(dedicatedSocket, email);
            NetworkUtils.sendObject(dedicatedSocket, mailbox.getEmailAddress());
            Object response = NetworkUtils.receiveObject(dedicatedSocket);
            return response.toString();
        }
    }

    public void handleEmailFiltering(String filter) throws Exception {
        if (!isConnected()) {
            throw new Exception("Server non raggiungibile");
        }

        List<Email> emails = fetchEmails(filter);
        mailbox.clearEmails();
        if (emails != null) {
            if (filter.equals(SENT_EMAILS)) {
                emails.forEach(mailbox::addSentEmail);
            } else {
                emails.forEach(mailbox::addReceivedEmail);
            }
            notifyEmailsFiltered(filter, emails);
        }
    }

    public String handleEmailSend(String[] recipients, String subject, String body) throws Exception {
        Email newEmail = createEmail(
                mailbox.getEmailAddress(),
                Arrays.asList(recipients),
                subject,
                body
        );
        return sendEmail(newEmail);
    }

    private void notifyEmailsFiltered(String filter, List<Email> emails) {
        for (EmailUpdateListener listener : listeners) {
            listener.onEmailsFiltered(filter, emails);
        }
    }

    public Email createReplyEmail(Email originalEmail) {
        Email replyTemplate = new Email();
        replyTemplate.setRecipients(Arrays.asList(originalEmail.getSender()));
        replyTemplate.setSubject("Re: " + originalEmail.getSubject());
        replyTemplate.setBody("\n\n----- Messaggio Originale -----\n" + originalEmail.getBody());
        replyTemplate.setSender(mailbox.getEmailAddress());
        return replyTemplate;
    }

    public Email createReplyAllEmail(Email originalEmail) {
        Set<String> recipients = new HashSet<>(originalEmail.getRecipients());
        recipients.add(originalEmail.getSender());
        recipients.remove(mailbox.getEmailAddress());

        Email replyAllTemplate = new Email();
        replyAllTemplate.setRecipients(new ArrayList<>(recipients));
        replyAllTemplate.setSubject("Re: " + originalEmail.getSubject());
        replyAllTemplate.setBody("\n\n----- Messaggio Originale -----\n" + originalEmail.getBody());
        replyAllTemplate.setSender(mailbox.getEmailAddress());
        return replyAllTemplate;
    }

    public Email createForwardEmail(Email originalEmail) {
        Email forwardTemplate = new Email();
        forwardTemplate.setSubject("Fwd: " + originalEmail.getSubject());
        forwardTemplate.setBody("\n\n----- Messaggio Inoltrato -----\n" +
                "Da: " + originalEmail.getSender() + "\n" +
                "A: " + String.join(", ", originalEmail.getRecipients()) + "\n" +
                "Oggetto: " + originalEmail.getSubject() + "\n\n" +
                originalEmail.getBody());
        forwardTemplate.setSender(mailbox.getEmailAddress());
        return forwardTemplate;
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
                    return (List<Email>) list;
                }
            }
            return List.of(); // Return empty list if response is not valid
        }
    }

    public Mailbox getMailbox() {
        return mailbox;
    }

    public void shutdown() {
        isShuttingDown = true;
        if (clientSocket != null && !clientSocket.isClosed()) {
            try {
                clientSocket.close();
                System.out.println("Socket client chiusa correttamente");
            } catch (IOException e) {
                System.err.println("Errore chiusura socket: " + e.getMessage());
            }
        }
        synchronized(connectionLock) {
            connectedProperty.set(false);
        }
        mailboxLock.writeLock().lock();
        try {
            mailbox.clearEmails();
        } finally {
            mailboxLock.writeLock().unlock();
        }

        // Shutdown executor
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            try {
                executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Clear listeners
        synchronized(listenersLock) {
            listeners.clear();
        }
    }

}