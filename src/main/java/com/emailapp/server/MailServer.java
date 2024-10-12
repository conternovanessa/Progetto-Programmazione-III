package com.emailapp.server;

import com.emailapp.client.Email;
import com.emailapp.EmailFileManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MailServer {
    private final Map<String, EmailAccount> accounts;

    public MailServer() {
        this.accounts = new HashMap<>();
    }

    public void createAccount(String emailAddress) {
        accounts.putIfAbsent(emailAddress, new EmailAccount(emailAddress));
    }


    public void sendEmail(Email email) {
        String sender = email.getSender();
        List<String> recipients = email.getRecipients();

        // Crea gli account se non esistono
        createAccount(sender);
        for (String recipient : recipients) {
            createAccount(recipient);
        }

        // Salva l'email nelle inbox dei destinatari e nella sent del mittente
        for (String recipient : recipients) {
            accounts.get(recipient).addToInbox(email);
            try {
                EmailFileManager.saveEmail(email, recipient);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Aggiungi l'email alla cartella sent del mittente
        accounts.get(sender).addToSent(email);
        try {
            EmailFileManager.saveEmail(email, sender);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public List<Email> getNewEmails(String recipient) {
        createAccount(recipient);
        return new ArrayList<>(accounts.get(recipient).getInbox());
    }

    public boolean deleteEmail(String emailId) {
        for (EmailAccount account : accounts.values()) {
            if (account.removeEmail(emailId)) {
                return true;
            }
        }
        return false;
    }

    public Map<String, EmailAccount> getAccounts() {
        return accounts;
    }
}