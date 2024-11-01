package com.emailapp.server.model;

import com.emailapp.client.model.Email;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class EmailAccount {
    private final String emailAddress;
    private final ObservableList<Email> inbox;
    private final ObservableList<Email> sent;

    public EmailAccount(String emailAddress) {
        this.emailAddress = emailAddress;
        this.inbox = FXCollections.observableArrayList();
        this.sent = FXCollections.observableArrayList();
    }

    public ObservableList<Email> getInbox() {
        return inbox;
    }

    public ObservableList<Email> getSent() {
        return sent;
    }

    public void addToInbox(Email email) {
        inbox.add(email);
    }

    public void addToSent(Email email) {
        sent.add(email);
    }

}