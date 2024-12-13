package com.emailapp.server.model;

import com.emailapp.util.Email;
import com.emailapp.server.controller.ServerController;
import com.emailapp.util.EmailFileManager;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class MailServer {
    private final Map<String, EmailAccount> accounts;
    private final ServerController serverController;
    private final ReadWriteLock serverLock = new ReentrantReadWriteLock(true); // Fair lock
    private final Map<String, Queue<Email>> messageQueues;
    private final List<ServerObserver> observers = Collections.synchronizedList(new ArrayList<>());
    private final Object OBSERVER_LOCK = new Object();
    private static final long LOCK_TIMEOUT = 5000; // 5 secondi timeout

    public MailServer(ServerController serverController) {
        this.accounts = new ConcurrentHashMap<>();
        this.messageQueues = new ConcurrentHashMap<>();
        this.serverController = serverController;
    }

    public void loadExistingEmails() {
        serverLock.writeLock().lock();
        try {
            List<String> emailAddresses = EmailFileManager.loadValidEmails()
                    .stream()
                    .map(email -> email.contains(",") ?
                            email.substring(0, email.indexOf(",")).trim() :
                            email.trim())
                    .collect(Collectors.toList());

            for (String emailAddress : emailAddresses) {
                createAccount(emailAddress);
                try {
                    List<Email> emails = EmailFileManager.loadEmails(emailAddress);
                    EmailAccount account = accounts.get(emailAddress);

                    if (account != null) {
                        for (Email email : emails) {
                            if (email.getSender().equals(emailAddress)) {
                                account.addToSent(email);
                            } else {
                                account.addToInbox(email);
                            }
                        }
                    }
                } catch (IOException e) {
                    serverController.logEvent("Errore nel caricamento delle email per " + emailAddress + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            serverController.logEvent("Errore nel caricamento degli indirizzi email validi: " + e.getMessage());
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    public void createAccount(String emailAddress) {
        serverLock.writeLock().lock();
        try {
            accounts.putIfAbsent(emailAddress, new EmailAccount(emailAddress));
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    private void validateEmail(String email) throws IllegalArgumentException {
        // Check for null or empty
        if (email == null || email.trim().isEmpty()) {
            serverController.logEvent("❌ Errore: Email vuota o null");
            throw new IllegalArgumentException("L'indirizzo email non può essere vuoto");
        }
        int atIndex = email.indexOf('@');
        if (atIndex == -1) {
            serverController.logEvent("❌ Errore: Manca la @ nell'indirizzo " + email);
            throw new IllegalArgumentException("L'indirizzo email deve contenere @");
        }
        if (atIndex == 0 || atIndex == email.length() - 1) {
            serverController.logEvent("❌ Errore: @ in posizione non valida in " + email);
            throw new IllegalArgumentException("@ non può essere all'inizio o alla fine dell'indirizzo");
        }
        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];
        if (!"progetto.com".equals(domain)) {
            serverController.logEvent("❌ Errore: Dominio non valido " + domain);
            throw new IllegalArgumentException("Il dominio deve essere progetto.com");
        }
        try {
            List<String> validUsernames = EmailFileManager.loadValidEmails()
                    .stream()
                    .map(e -> e.split("@")[0])
                    .collect(Collectors.toList());

            if (!validUsernames.contains(username)) {
                serverController.logEvent("❌ Errore: Username non valido " + username);
                throw new IllegalArgumentException("Username non valido. Utenti validi: " + String.join(", ", validUsernames));
            }
        } catch (IOException e) {
            serverController.logEvent("❌ Errore di sistema nella validazione username");
            throw new RuntimeException("Errore nella verifica dell'username", e);
        }
    }

    public void sendEmail(Email email) throws IOException {
        try {
            if (!serverLock.writeLock().tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new IOException("Timeout durante l'acquisizione del lock");
            }
            try {
                // 1. Validation
                validateEmail(email.getSender());
                for (String recipient : email.getRecipients()) {
                    validateEmail(recipient);
                }

                String sender = email.getSender();
                List<String> recipients = email.getRecipients();

                // 2. Account creation
                createAccount(sender);
                recipients.forEach(this::createAccount);

                // 3. Process sender's copy
                int sentEmailId = EmailFileManager.getNextId();
                Email senderCopy = new Email(sender, recipients, email.getSubject(), email.getBody());
                senderCopy.setId(sentEmailId);

                try {
                    EmailFileManager.saveEmail(senderCopy, sender);
                    accounts.get(sender).addToSent(senderCopy);
                    synchronized(OBSERVER_LOCK) {
                        notifyEmailSent(senderCopy);
                    }
                } catch (IOException e) {
                    serverController.logEvent("❌ Errore salvataggio email per mittente " + sender);
                    throw e;
                }

                // 4. Process recipients' copies
                for (String recipient : recipients) {
                    int recipientEmailId = EmailFileManager.getNextId();
                    Email recipientCopy = new Email(sender, recipients, email.getSubject(), email.getBody());
                    recipientCopy.setId(recipientEmailId);

                    try {
                        EmailFileManager.saveEmail(recipientCopy, recipient);
                        accounts.get(recipient).addToInbox(recipientCopy);
                        queueEmail(recipientCopy, recipient);
                        synchronized(OBSERVER_LOCK) {
                            notifyEmailReceived(recipientCopy);
                        }
                    } catch (IOException e) {
                        serverController.logEvent("❌ Errore salvataggio email per destinatario " + recipient);
                        throw e;
                    }
                }
            } finally {
                serverLock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operazione interrotta durante l'attesa del lock");
        }
    }

    private void queueEmail(Email email, String recipient) {
        messageQueues.computeIfAbsent(recipient, k -> new ConcurrentLinkedQueue<>())
                .offer(email);
    }

    public List<Email> retrieveQueuedEmails(String recipient) {
        serverLock.readLock().lock();
        try {
            Queue<Email> queue = messageQueues.get(recipient);
            if (queue == null) {
                return new ArrayList<>();
            }
            List<Email> emails = new ArrayList<>();
            Email email;
            while ((email = queue.poll()) != null) {
                emails.add(email);
            }
            return emails;
        } finally {
            serverLock.readLock().unlock();
        }
    }

    public List<Email> getEmailsForUser(String recipient) {
        serverLock.readLock().lock();
        try {
            EmailAccount account = accounts.get(recipient);
            if (account == null) {
                return new ArrayList<>();
            }
            List<Email> allEmails = new ArrayList<>();
            allEmails.addAll(account.getInbox());
            allEmails.addAll(account.getSent());
            return allEmails;
        } finally {
            serverLock.readLock().unlock();
        }
    }

    public boolean deleteEmail(int emailId, String requestingUser) {
        serverLock.writeLock().lock();
        try {
            // 1. Get user account
            EmailAccount account = accounts.get(requestingUser);
            if (account == null) {
                serverController.logEvent("⚠️ Account non trovato: " + requestingUser);
                return false;
            }

            boolean deletedFromInbox = account.removeFromInbox(emailId);
            boolean deletedFromSent = account.removeFromSent(emailId);

            if (deletedFromInbox || deletedFromSent) {
                try {
                    EmailFileManager.deleteEmail(emailId, requestingUser);
                    // Notify observers
                    notifyEmailDeleted(emailId);
                    return true;
                } catch (IOException e) {
                    serverController.logEvent("❌ Errore eliminazione file email: " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }

            serverController.logEvent("⚠️ Email " + emailId + " non trovata per " + requestingUser);
            return false;
        } finally {
            serverLock.writeLock().unlock();
        }
    }

    public List<Email> getEmailsFromSender(String sender) {
        serverLock.readLock().lock();
        try {
            EmailAccount account = accounts.get(sender);
            return account != null ? new ArrayList<>(account.getSent()) : new ArrayList<>();
        } finally {
            serverLock.readLock().unlock();
        }
    }

    public List<Email> getEmailsForRecipient(String recipient) {
        serverLock.readLock().lock();
        try {
            EmailAccount account = accounts.get(recipient);
            return account != null ? new ArrayList<>(account.getInbox()) : new ArrayList<>();
        } finally {
            serverLock.readLock().unlock();
        }
    }

    public void addObserver(ServerObserver observer) {
        synchronized(OBSERVER_LOCK) {
            observers.add(observer);
        }
    }

    public void removeObserver(ServerObserver observer) {
        synchronized(OBSERVER_LOCK) {
            observers.remove(observer);
        }
    }

    private void notifyEmailSent(Email email) {
        synchronized(OBSERVER_LOCK) {
            for (ServerObserver observer : observers) {
                observer.onEmailSent(email);
            }
        }
    }

    private void notifyEmailReceived(Email email) {
        synchronized(OBSERVER_LOCK) {
            for (ServerObserver observer : observers) {
                observer.onEmailReceived(email);
            }
        }
    }


    private void notifyEmailDeleted(int emailId) {
        synchronized(OBSERVER_LOCK) {
            for (ServerObserver observer : observers) {
                observer.onEmailDeleted(emailId);
            }
        }
    }
}