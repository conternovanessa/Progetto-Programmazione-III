package com.emailapp.util;

import javafx.beans.property.SimpleBooleanProperty;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Email implements Serializable {
    private int id;
    private String sender;
    private List<String> recipients;
    private String subject;
    private String body;
    private LocalDateTime sentDate;
    private boolean read;
    private transient SimpleBooleanProperty readProperty;

    public Email() {
        this.sentDate = LocalDateTime.now();
        this.read = false;
        this.readProperty = new SimpleBooleanProperty(false);
        this.recipients = new ArrayList<>();
    }

    public Email(String sender, List<String> recipients, String subject, String body) {
        this.sender = sender;
        this.recipients = recipients;
        this.subject = subject;
        this.body = body;
        this.sentDate = LocalDateTime.now();
        this.read = false;
        this.readProperty = new SimpleBooleanProperty(false);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
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

    public void setSentDate(LocalDateTime sentDate) {
        this.sentDate = sentDate;
    }

    public boolean isRead() {
        if (readProperty == null) {
            readProperty = new SimpleBooleanProperty(read);
        }
        return readProperty.get();
    }

    public void setRead(boolean read) {
        this.read = read;
        if (readProperty == null) {
            readProperty = new SimpleBooleanProperty();
        }
        this.readProperty.set(read);
    }

    public SimpleBooleanProperty readProperty() {
        if (readProperty == null) {
            readProperty = new SimpleBooleanProperty(read);
        }
        return readProperty;
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

}
