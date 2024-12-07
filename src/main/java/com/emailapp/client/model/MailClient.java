package com.emailapp.client.model;

import com.emailapp.util.NetworkUtils;
import java.io.*;
import java.net.Socket;
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

    public MailClient(String emailAddress) {
        // Rimuovi la porta se presente nell'indirizzo email
        if (emailAddress.contains(",")) {
            emailAddress = emailAddress.split(",")[0].trim();
        }
        this.mailbox = new Mailbox(emailAddress);
        this.executorService = Executors.newCachedThreadPool();
        this.connectedProperty = new SimpleBooleanProperty(false);
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
            String command = filter.equals("Email inviate") ? "FETCH_SENT_EMAILS" : "FETCH_RECEIVED_EMAILS";
            NetworkUtils.sendObject(socket, command);
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
        try (Socket controlSocket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(controlSocket, "REQUEST_WRITE_SOCKET");
            NetworkUtils.sendObject(controlSocket, mailbox.getEmailAddress());

            int dedicatedPort = (int) NetworkUtils.receiveObject(controlSocket);
            if (dedicatedPort == -1) {
                throw new IOException("Server failed to allocate dedicated port");
            }

            try (Socket dedicatedSocket = new Socket(SERVER_ADDRESS, dedicatedPort)) {
                NetworkUtils.sendObject(dedicatedSocket, email);
                NetworkUtils.sendObject(dedicatedSocket, mailbox.getEmailAddress());
                return (String) NetworkUtils.receiveObject(dedicatedSocket);
            }
        }
    }



    public Email createReplyEmail(String action, int emailId) throws IOException, ClassNotFoundException {
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