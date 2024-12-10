package com.emailapp.client.model;

import com.emailapp.util.Email;
import com.emailapp.util.EmailFileManager;
import com.emailapp.util.NetworkUtils;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        listeners.add(listener);
    }

    public void removeEmailUpdateListener(EmailUpdateListener listener) {
        listeners.remove(listener);
    }

    public void filterEmails(String filter) throws Exception {
        if (isConnected()) {
            try {
                List<Email> emails = fetchEmails(filter);
                for (EmailUpdateListener listener : listeners) {
                    listener.onEmailsFiltered(filter, emails);
                }
            } catch (Exception e) {
                for (EmailUpdateListener listener : listeners) {
                    listener.onEmailUpdateError(e);
                }
            }
        } else {
            // Offline filtering using local mailbox
            List<Email> localEmails = filter.equals(SENT_EMAILS) ?
                    mailbox.getSentEmails() :
                    mailbox.getReceivedEmails();

            for (EmailUpdateListener listener : listeners) {
                listener.onEmailsFiltered(filter, new ArrayList<>(localEmails));
            }
        }
    }


    public void pollForNewEmails() {
        try {
            List<Email> newEmails = checkNewEmails(); // questo metodo già esiste
            if (newEmails != null && !newEmails.isEmpty()) {
                for (EmailUpdateListener listener : listeners) {
                    listener.onNewEmailsReceived(newEmails);
                }
            }
        } catch (Exception e) {
            for (EmailUpdateListener listener : listeners) {
                listener.onEmailUpdateError(e);
            }
        }
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

    public String sendEmail(Email email) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            // Debug log
            System.out.println("Attempting to send email from: " + mailbox.getEmailAddress());

            NetworkUtils.sendObject(socket, "SEND_EMAIL");
            NetworkUtils.sendObject(socket, email);
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());

            Object response = NetworkUtils.receiveObject(socket);
            System.out.println("Server response: " + response);

            return response.toString();
        } catch (Exception e) {
            System.err.println("Error sending email: " + e.getMessage());
            e.printStackTrace();
            throw e;
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


    public void handleEmailRead(Email email) {
        if (!email.isRead()) {
            email.setRead(true);
            try {
                EmailFileManager.updateEmailReadStatus(email, mailbox.getEmailAddress());
                mailbox.updateEmailReadStatus(email);
                notifyEmailMarkedAsRead(email);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



    // Move email sending logic here
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
        executorService.shutdown();
    }
}