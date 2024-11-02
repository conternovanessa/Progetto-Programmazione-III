package com.emailapp.server.model;

import com.emailapp.client.model.Email;
import com.emailapp.server.controller.ServerController;
import com.emailapp.util.EmailFileManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MailServer {
    private final Map<String, EmailAccount> accounts;
    private final ServerController serverController;
    private final Object accountLock = new Object();
    private final Object emailLock = new Object();

    public MailServer(ServerController serverController) {
        this.accounts = new HashMap<>();
        this.serverController = serverController;
    }

    public void createAccount(String emailAddress) {
        accounts.putIfAbsent(emailAddress, new EmailAccount(emailAddress));
    }

    public void sendEmail(Email email) {
        synchronized (emailLock) {
            String sender = email.getSender();
            List<String> recipients = email.getRecipients();

            synchronized (accountLock) {
                createAccount(sender);
                recipients.forEach(this::createAccount);
            }

            for (String recipient : recipients) {
                int uniqueEmailId = EmailFileManager.getNextId();

                Email recipientCopy = createEmailCopy(email);
                recipientCopy.setId(uniqueEmailId);
                recipientCopy.setRecipients(email.getRecipients());

                synchronized (accountLock) {
                    accounts.get(recipient).addToInbox(recipientCopy);
                }

                try {
                    EmailFileManager.saveEmail(recipientCopy, recipient);
                } catch (IOException e) {
                    serverController.logEvent("Errore durante il salvataggio dell'email per " + recipient + ": " + e.getMessage());
                }
            }

            String recipientsStr = String.join(", ", recipients);
            serverController.logEvent("Email inviate da : " + sender + " a: " + recipientsStr);
        }
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
        synchronized (emailLock) {
            synchronized (accountLock) {
                createAccount(recipient);
                return new ArrayList<>(accounts.get(recipient).getInbox());
            }
        }
    }


    public boolean deleteEmail(int emailId, String requestingUser) {
        synchronized (emailLock) {
            synchronized (accountLock) {
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
    }

}