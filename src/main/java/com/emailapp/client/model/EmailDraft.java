package com.emailapp.client.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.Serializable;
import java.util.UUID;

public class EmailDraft implements Serializable {
    private final String id;
    private final StringProperty sender;
    private final StringProperty recipients;
    private final StringProperty subject;
    private final StringProperty body;

    public EmailDraft(String sender) {
        this.id = UUID.randomUUID().toString();
        this.sender = new SimpleStringProperty(sender);
        this.recipients = new SimpleStringProperty("");
        this.subject = new SimpleStringProperty("");
        this.body = new SimpleStringProperty("");
    }

    public String getId() {
        return id;
    }

    public String getSender() {
        return sender.get();
    }

    public StringProperty senderProperty() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender.set(sender);
    }

    public String getRecipients() {
        return recipients.get();
    }

    public StringProperty recipientsProperty() {
        return recipients;
    }

    public void setRecipients(String recipients) {
        this.recipients.set(recipients);
    }

    public String getSubject() {
        return subject.get();
    }

    public StringProperty subjectProperty() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject.set(subject);
    }

    public String getBody() {
        return body.get();
    }

    public StringProperty bodyProperty() {
        return body;
    }

    public void setBody(String body) {
        this.body.set(body);
    }
}