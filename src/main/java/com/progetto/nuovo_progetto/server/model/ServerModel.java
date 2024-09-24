package com.progetto.nuovo_progetto.server.model;

import com.progetto.nuovo_progetto.common.Email;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import java.util.ArrayList;
import java.util.List;

public class ServerModel {
    private ObservableMap<String, List<Email>> mailboxes;
    private ObservableList<String> logEntries;

    public ServerModel() {
        mailboxes = FXCollections.observableHashMap();
        logEntries = FXCollections.observableArrayList();
    }

    public void addEmail(String recipient, Email email) {
        mailboxes.computeIfAbsent(recipient, k -> new ArrayList<>()).add(email);
        logEntries.add("Email added to " + recipient + "'s mailbox");
    }

    public List<Email> getEmails(String user) {
        return mailboxes.getOrDefault(user, new ArrayList<>());
    }

    public ObservableList<String> getLogEntries() {
        return logEntries;
    }

    public void addLogEntry(String entry) {
        logEntries.add(entry);
    }
}