package com.emailapp.server.model;

import com.emailapp.util.Email;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class EmailAccount {
    private final String emailAddress;
    private final ObservableList<Email> inbox;
    private final ObservableList<Email> sent;
    private final ReadWriteLock accountLock = new ReentrantReadWriteLock();

    public EmailAccount(String emailAddress) {
        this.emailAddress = emailAddress;
        this.inbox = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
        this.sent = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
    }

    public ObservableList<Email> getInbox() {
        accountLock.readLock().lock();
        try {
            return FXCollections.unmodifiableObservableList(inbox);
        } finally {
            accountLock.readLock().unlock();
        }
    }

    public ObservableList<Email> getSent() {
        accountLock.readLock().lock();
        try {
            return FXCollections.unmodifiableObservableList(sent);
        } finally {
            accountLock.readLock().unlock();
        }
    }

    public void addToInbox(Email email) {
        accountLock.writeLock().lock();
        try {
            if (inbox.stream().noneMatch(e -> e.getId() == email.getId())) {
                inbox.add(email);
            }
        } finally {
            accountLock.writeLock().unlock();
        }
    }

    public void addToSent(Email email) {
        accountLock.writeLock().lock();
        try {
            if (sent.stream().noneMatch(e -> e.getId() == email.getId())) {
                sent.add(email);
            }
        } finally {
            accountLock.writeLock().unlock();
        }
    }


    public boolean removeFromInbox(int emailId) {
        accountLock.writeLock().lock();
        try {
            return inbox.removeIf(email -> email.getId() == emailId);
        } finally {
            accountLock.writeLock().unlock();
        }
    }

    public boolean removeFromSent(int emailId) {
        accountLock.writeLock().lock();
        try {
            return sent.removeIf(email -> email.getId() == emailId);
        } finally {
            accountLock.writeLock().unlock();
        }
    }

    public Email getEmailById(int emailId) {
        accountLock.readLock().lock();
        try {
            Email inboxEmail = inbox.stream()
                    .filter(e -> e.getId() == emailId)
                    .findFirst()
                    .orElse(null);

            if (inboxEmail != null) {
                return inboxEmail;
            }

            return sent.stream()
                    .filter(e -> e.getId() == emailId)
                    .findFirst()
                    .orElse(null);
        } finally {
            accountLock.readLock().unlock();
        }
    }

    public String getEmailAddress() {
        return emailAddress;
    }
}