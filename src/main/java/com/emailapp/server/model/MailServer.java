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

        // Create independent copies only for recipients
        for (String recipient : recipients) {
            int uniqueEmailId = EmailFileManager.getNextId();

            Email recipientCopy = createEmailCopy(email);
            recipientCopy.setId(uniqueEmailId);
            // Keep all recipients for Reply All functionality
            recipientCopy.setRecipients(email.getRecipients());

            accounts.get(recipient).addToInbox(recipientCopy);
            try {
                EmailFileManager.saveEmail(recipientCopy, recipient);
            } catch (IOException e) {
                serverController.logEvent("Error saving email for " + recipient + ": " + e.getMessage());
            }
        }

        String recipientsStr = String.join(", ", recipients);
        serverController.logEvent("Emails sent by " + sender + " to " + recipientsStr);
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