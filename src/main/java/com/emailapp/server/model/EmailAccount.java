package com.emailapp.server.model;
import com.emailapp.client.model.Email;
import java.util.ArrayList;
import java.util.List;

public class EmailAccount {
    private String emailAddress;
    private List<Email> inbox;

    public EmailAccount(String emailAddress) {
        this.emailAddress = emailAddress;
        this.inbox = new ArrayList<>();
    }

    public void addEmail(Email email) {
        inbox.add(email);
    }

    public List<Email> getNewEmails() {
        List<Email> newEmails = new ArrayList<>(inbox);
        inbox.clear();
        return newEmails;
    }

    public boolean deleteEmail(String emailId) {
        return inbox.removeIf(email -> email.getId().equals(emailId));
    }

    public String getEmailAddress() {
        return emailAddress;
    }
}