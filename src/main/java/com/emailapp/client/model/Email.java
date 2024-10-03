package com.emailapp.client.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Email implements Serializable {
    private String id;
    private String sender;
    private List<String> recipients;
    private String subject;
    private String body;
    private LocalDateTime sentDate;

    public Email(String sender, List<String> recipients, String subject, String body) {
        this.id = generateId();
        this.sender = sender;
        this.recipients = new ArrayList<>(recipients);
        this.subject = subject;
        this.body = body;
        this.sentDate = LocalDateTime.now();
    }

    private String generateId() {
        return System.currentTimeMillis() + "-" + Math.random();
    }

    // Getter e Setter
    public String getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public List<String> getRecipients() {
        return new ArrayList<>(recipients);
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = new ArrayList<>(recipients);
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public LocalDateTime getSentDate() {
        return sentDate;
    }

    @Override
    public String toString() {
        return "Email{" +
                "id='" + id + '\'' +
                ", sender='" + sender + '\'' +
                ", recipients=" + recipients +
                ", subject='" + subject + '\'' +
                ", sentDate=" + sentDate +
                '}';
    }
}