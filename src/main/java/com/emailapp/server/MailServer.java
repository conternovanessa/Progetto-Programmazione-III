package com.emailapp.server;

import com.emailapp.client.Email;
import com.emailapp.EmailFileManager;

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
            serverController.logEvent(emailType + " sent by " + sender + " to " + recipientsStr);
        } else {
            serverController.logEvent(emailType + " partially sent by " + sender + " to " + recipientsStr + " (some errors occurred)");
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

    public boolean deleteEmail(long emailId, String userEmail) {
        EmailAccount account = accounts.get(userEmail);
        if (account != null) {
            boolean deleted = account.removeEmail(emailId);
            if (deleted) {
                try {
                    EmailFileManager.deleteEmail(String.valueOf(emailId), userEmail);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.err.println("Failed to delete email file for ID: " + emailId + " in account: " + userEmail);
                }
            }
            return deleted;
        }
        return false;
    }

public Map<String, EmailAccount> getAccounts() {
        return accounts;
    }
}