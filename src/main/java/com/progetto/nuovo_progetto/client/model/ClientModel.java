package com.progetto.nuovo_progetto.client.model;

import com.progetto.nuovo_progetto.common.Email;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ClientModel {
    private String emailAddress;
    private ObservableList<Email> inbox;

    public ClientModel(String emailAddress) {
        this.emailAddress = emailAddress;
        this.inbox = FXCollections.observableArrayList();
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public ObservableList<Email> getInbox() {
        return inbox;
    }

    public void addEmail(Email email) {
        inbox.add(email);
    }

    // Altri metodi per gestire le email
}