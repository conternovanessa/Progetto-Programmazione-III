package com.emailapp.client.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Mailbox {
    private String emailAddress;
    private ObservableList<Email> receivedEmails;
    private ObservableList<Email> sentEmails;

    public Mailbox(String emailAddress) {
        this.emailAddress = emailAddress;
        this.receivedEmails = FXCollections.observableArrayList();
        this.sentEmails = FXCollections.observableArrayList();
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public ObservableList<Email> getReceivedEmails() {
        return receivedEmails;
    }

    public ObservableList<Email> getSentEmails() {
        return sentEmails;
    }

    public void addReceivedEmail(Email email) {
        receivedEmails.add(email);
    }

    public void addSentEmail(Email email) {
        sentEmails.add(email);
    }

    public void removeReceivedEmail(Email email) {
        receivedEmails.remove(email);
    }

    public void removeSentEmail(Email email) {
        sentEmails.remove(email);
    }

    public ObservableList<Email> getAllEmails() {
        ObservableList<Email> allEmails = FXCollections.observableArrayList();
        allEmails.addAll(receivedEmails);
        allEmails.addAll(sentEmails);
        return allEmails;
    }

    public int getTotalEmailCount() {
        return receivedEmails.size() + sentEmails.size();
    }

    @Override
    public String toString() {
        return "Mailbox{" +
                "emailAddress='" + emailAddress + '\'' +
                ", receivedEmails=" + receivedEmails.size() +
                ", sentEmails=" + sentEmails.size() +
                '}';
    }
}