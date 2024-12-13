package com.emailapp.server.model;

import com.emailapp.util.Email;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class EmailAccount {
    private final String emailAddress;
    private final ObservableList<Email> inbox;
    private final ObservableList<Email> sent;
    private final ReadWriteLock accountLock = new ReentrantReadWriteLock(true);
    private static final long LOCK_TIMEOUT = 3000;

    public EmailAccount(String emailAddress) {
        this.emailAddress = emailAddress;
        this.inbox = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
        this.sent = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
    }

    public ObservableList<Email> getInbox() {
        try {
            if (!accountLock.readLock().tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout durante l'accesso alla inbox");
            }
            try {
                return FXCollections.unmodifiableObservableList(inbox);
            } finally {
                accountLock.readLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operazione interrotta durante l'accesso alla inbox");
        }
    }

    public ObservableList<Email> getSent() {
        try {
            if (!accountLock.readLock().tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout durante l'accesso alle email inviate");
            }
            try {
                return FXCollections.unmodifiableObservableList(sent);
            } finally {
                accountLock.readLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operazione interrotta durante l'accesso alle email inviate");
        }
    }

    public void addToInbox(Email email) {
        try {
            if (!accountLock.writeLock().tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout durante l'aggiunta alla inbox");
            }
            try {
                if (inbox.stream().noneMatch(e -> e.getId() == email.getId())) {
                    Platform.runLater(() -> inbox.add(email));
                }
            } finally {
                accountLock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operazione interrotta durante l'aggiunta alla inbox");
        }
    }

    public void addToSent(Email email) {
        try {
            if (!accountLock.writeLock().tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout durante l'aggiunta alle email inviate");
            }
            try {
                if (sent.stream().noneMatch(e -> e.getId() == email.getId())) {
                    Platform.runLater(() -> sent.add(email));
                }
            } finally {
                accountLock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operazione interrotta durante l'aggiunta alle email inviate");
        }
    }

    public boolean removeFromInbox(int emailId) {
        try {
            if (!accountLock.writeLock().tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout durante la rimozione dalla inbox");
            }
            try {
                return inbox.removeIf(email -> email.getId() == emailId);
            } finally {
                accountLock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operazione interrotta durante la rimozione dalla inbox");
        }
    }

    public boolean removeFromSent(int emailId) {
        try {
            if (!accountLock.writeLock().tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout durante la rimozione dalle email inviate");
            }
            try {
                return sent.removeIf(email -> email.getId() == emailId);
            } finally {
                accountLock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operazione interrotta durante la rimozione dalle email inviate");
        }
    }

    public Email getEmailById(int emailId) {
        try {
            if (!accountLock.readLock().tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout durante la ricerca email");
            }
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operazione interrotta durante la ricerca email");
        }
    }

    public String getEmailAddress() {
        return emailAddress;
    }
}
