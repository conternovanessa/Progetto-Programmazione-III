package com.emailapp.server.model;

import com.emailapp.client.model.Email;
import com.emailapp.server.controller.ServerController;
import com.emailapp.util.EmailFileManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MailServer {
    private final Map<String, EmailAccount> accounts;
    private final ServerController serverController;

    public MailServer(ServerController serverController) {
        this.accounts = new HashMap<>();
        this.serverController = serverController;
    }

    public void createAccount(String emailAddress) {
        accounts.putIfAbsent(emailAddress, new EmailAccount(emailAddress));
    }

    public void sendEmail(Email email) {
        String sender = email.getSender();
        List<String> recipients = email.getRecipients();

        // Create accounts if they don't exist
        createAccount(sender);
        recipients.forEach(this::createAccount);

        // Determine email type
        String emailType = determineEmailType(email);

        // Assign a new ID to the email
        int newId = EmailFileManager.getNextId();
        email.setId(newId);

        // Save the email in recipients' inboxes and sender's sent folder
        boolean allSaved = true;
        for (String recipient : recipients) {
            accounts.get(recipient).addToInbox(email);
            try {
                EmailFileManager.saveEmail(email, recipient);
            } catch (IOException e) {
                allSaved = false;
                serverController.logEvent("Error saving email for " + recipient + ": " + e.getMessage());
            }
        }

        // Add the email to sender's sent folder
        accounts.get(sender).addToSent(email);
        try {
            EmailFileManager.saveEmail(email, sender);
        } catch (IOException e) {
            allSaved = false;
            serverController.logEvent("Error saving sent email for " + sender + ": " + e.getMessage());
        }

        // Log the email action
        String recipientsStr = recipients.stream().collect(Collectors.joining(", "));
        if (allSaved) {
            serverController.logEvent(emailType + " (ID: " + newId + ") sent by " + sender + " to " + recipientsStr);
        } else {
            serverController.logEvent(emailType + " (ID: " + newId + ") partially sent by " + sender + " to " + recipientsStr + " (some errors occurred)");
        }
    }

    private String determineEmailType(Email email) {
        if (email.getSubject().toLowerCase().startsWith("re:")) {
            return "Reply email";
        } else if (email.getSubject().toLowerCase().startsWith("fwd:")) {
            return "Forwarded email";
        } else {
            return "New email";
        }
    }

    public List<Email> getNewEmails(String recipient) {
        createAccount(recipient);
        return new ArrayList<>(accounts.get(recipient).getInbox());
    }

    public synchronized boolean deleteEmail(Long emailId, String requestingUser) {
        try {
            Email email = getEmailById(emailId);
            if (email == null) {
                return false;
            }

            // Verifica i permessi
            boolean isSender = email.getSender().equals(requestingUser);
            boolean isRecipient = email.getRecipients().contains(requestingUser);

            if (!isSender && !isRecipient) {
                return false;
            }

            // Elimina l'email dal filesystem per l'utente richiedente
            String userDirectory = System.getProperty("user.home") +
                    File.separator + "EmailApp" +
                    File.separator + requestingUser;
            File emailFile = new File(userDirectory, "email_" + emailId + ".txt");

            if (emailFile.exists()) {
                return emailFile.delete();
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Email getEmailById(Long emailId) {
        if (emailId == null) {
            return null;
        }

        // First check emails in memory
        for (EmailAccount account : accounts.values()) {
            // Check inbox emails
            for (Email email : account.getInbox()) {
                if (email.getId() == emailId) {
                    return email;
                }
            }

            // Check sent emails
            for (Email email : account.getSent()) {
                if (email.getId() == emailId) {
                    return email;
                }
            }
        }

        // If not found in memory, try to load from disk by checking all user directories
        File emailAppDir = new File(System.getProperty("user.home"), "EmailApp");
        if (emailAppDir.exists() && emailAppDir.isDirectory()) {
            for (File userDir : emailAppDir.listFiles()) {
                if (userDir.isDirectory()) {
                    String userEmail = userDir.getName();
                    try {
                        List<Email> userEmails = EmailFileManager.loadEmails(userEmail);
                        for (Email email : userEmails) {
                            if (email.getId() == emailId) {
                                return email;
                            }
                        }
                    } catch (IOException e) {
                        serverController.logEvent("Error loading emails for user " + userEmail + ": " + e.getMessage());
                    }
                }
            }
        }

        return null;
    }
    public Map<String, EmailAccount> getAccounts() {
        return accounts;
    }
}