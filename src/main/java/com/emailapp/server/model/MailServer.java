package com.emailapp.server.model;

import com.emailapp.client.model.Email;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

public class MailServer {
    private Map<String, EmailAccount> accounts;

    public MailServer() {
        this.accounts = new ConcurrentHashMap<>();
    }

    public void addAccount(String email) {
        accounts.putIfAbsent(email, new EmailAccount(email));
    }

    public boolean deliverEmail(Email email) {
        for (String recipient : email.getRecipients()) {
            EmailAccount account = accounts.get(recipient);
            if (account != null) {
                account.addEmail(email);
            } else {
                // Handle undelivered email
                return false;
            }
        }
        return true;
    }

    public List<Email> getNewEmails(String emailAddress) {
        EmailAccount account = accounts.get(emailAddress);
        if (account != null) {
            return account.getNewEmails();
        }
        return List.of();
    }

    public boolean deleteEmail(String emailId) {
        for (EmailAccount account : accounts.values()) {
            if (account.deleteEmail(emailId)) {
                return true;
            }
        }
        return false;
    }
}