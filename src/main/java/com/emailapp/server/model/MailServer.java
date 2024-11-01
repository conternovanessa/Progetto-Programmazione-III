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

        createAccount(sender);
        recipients.forEach(this::createAccount);

        // Generate a single ID for all copies of the email
        int emailId = EmailFileManager.getNextId();
        email.setId(emailId);

        // Save copies for all recipients with the same ID
        for (String recipient : recipients) {
            Email recipientCopy = createEmailCopy(email);
            // Keep the same ID for all copies
            recipientCopy.setId(emailId);
            accounts.get(recipient).addToInbox(recipientCopy);
            try {
                EmailFileManager.saveEmail(recipientCopy, recipient);
            } catch (IOException e) {
                serverController.logEvent("Error saving email for " + recipient + ": " + e.getMessage());
            }
        }

        // Save sender's copy with the same ID
        Email senderCopy = createEmailCopy(email);
        senderCopy.setId(emailId);
        accounts.get(sender).addToSent(senderCopy);
        try {
            EmailFileManager.saveEmail(senderCopy, sender);
        } catch (IOException e) {
            serverController.logEvent("Error saving sent email for " + sender + ": " + e.getMessage());
        }

        String recipientsStr = String.join(", ", recipients);
        serverController.logEvent("Email (ID: " + emailId + ") sent by " + sender + " to " + recipientsStr);
    }

    private Email createEmailCopy(Email original) {
        Email copy = new Email();
        copy.setId(original.getId());
        copy.setSender(original.getSender());
        copy.setRecipients(new ArrayList<>(original.getRecipients()));
        copy.setSubject(original.getSubject());
        copy.setBody(original.getBody());
        copy.setSentDate(original.getSentDate());
        copy.setRead(false);
        return copy;
    }

    public List<Email> getNewEmails(String recipient) {
        createAccount(recipient);
        return new ArrayList<>(accounts.get(recipient).getInbox());
    }


    public synchronized boolean deleteEmail(int emailId, String requestingUser) {
        EmailAccount account = accounts.get(requestingUser);
        if (account == null) {
            return false;
        }

        boolean deletedFromInbox = account.getInbox().removeIf(email -> email.getId() == emailId);
        boolean deletedFromSent = account.getSent().removeIf(email -> email.getId() == emailId);

        if (deletedFromInbox || deletedFromSent) {
            try {
                EmailFileManager.deleteEmail(emailId, requestingUser);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

}