package com.emailapp.client.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class Email implements Serializable {
    private static final long serialVersionUID = 2L;

    private int id;
    private String sender;
    private List<String> recipients;
    private String subject;
    private String body;
    private LocalDateTime sentDate;
    private boolean deleted;
    private boolean read;

    public Email() {
        this.sentDate = LocalDateTime.now();
        this.deleted = false;
        this.read = false;
    }

    public Email(String sender, List<String> recipients, String subject, String body) {
        this();
        this.sender = sender;
        this.recipients = recipients;
        this.subject = subject;
        this.body = body;
    }

    public int getId() { return id; }
    public String getSender() { return sender; }
    public List<String> getRecipients() { return recipients; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public LocalDateTime getSentDate() { return sentDate; }
    public boolean isRead() { return read; }

    public void setId(int id) { this.id = id; }
    public void setSender(String sender) { this.sender = sender; }
    public void setRecipients(List<String> recipients) { this.recipients = recipients; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setBody(String body) { this.body = body; }
    public void setSentDate(LocalDateTime sentDate) { this.sentDate = sentDate; }
    public void setRead(boolean read) { this.read = read; }

    public boolean isEmpty() {
        return sender == null && recipients == null && subject == null && body == null;
    }

    @Override
    public String toString() {
        return "Email{" +
                "id=" + id +
                ", sender='" + sender + '\'' +
                ", recipients=" + recipients +
                ", subject='" + subject + '\'' +
                ", sentDate=" + sentDate +
                ", read=" + read +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return id == email.id &&
                Objects.equals(sender, email.sender) &&
                Objects.equals(recipients, email.recipients) &&
                Objects.equals(subject, email.subject) &&
                Objects.equals(sentDate, email.sentDate);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}