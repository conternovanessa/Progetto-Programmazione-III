package com.emailapp.server.model;

import com.emailapp.util.Email;
import com.emailapp.server.controller.ServerController;
import com.emailapp.util.EmailFileManager;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class MailServer {
    private final Map<String, EmailAccount> accounts;
    private final ServerController serverController;
    private final ReadWriteLock serverLock = new ReentrantReadWriteLock();
    private final Map<String, Queue<Email>> messageQueues;

    public MailServer(ServerController serverController) {
        this.accounts = new ConcurrentHashMap<>();
        this.messageQueues = new ConcurrentHashMap<>();
        this.serverController = serverController;
        loadExistingEmails();
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

        // Check @ presence and position
        int atIndex = email.indexOf('@');
        if (atIndex == -1) {
            serverController.logEvent("❌ Errore: Manca la @ nell'indirizzo " + email);
            throw new IllegalArgumentException("L'indirizzo email deve contenere @");
        }
        if (atIndex == 0 || atIndex == email.length() - 1) {
            serverController.logEvent("❌ Errore: @ in posizione non valida in " + email);
            throw new IllegalArgumentException("@ non può essere all'inizio o alla fine dell'indirizzo");
        }

        // Split and validate parts
        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];

        // Validate domain
        if (!"progetto.com".equals(domain)) {
            serverController.logEvent("❌ Errore: Dominio non valido " + domain);
            throw new IllegalArgumentException("Il dominio deve essere progetto.com");
        }

        // Validate username against valid emails
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
        serverLock.writeLock().lock();
        try {
            // Step 1: Validate sender and recipients
            validateEmail(email.getSender());
            for (String recipient : email.getRecipients()) {
                validateEmail(recipient);
            }

            String sender = email.getSender();
            List<String> recipients = email.getRecipients();

            // Step 2: Create accounts if needed
            createAccount(sender);
            recipients.forEach(this::createAccount);

            // Step 3: Process sender's copy
            int sentEmailId = EmailFileManager.getNextId();
            Email senderCopy = new Email(sender, recipients, email.getSubject(), email.getBody());
            senderCopy.setId(sentEmailId);

            try {
                EmailFileManager.saveEmail(senderCopy, sender);
                accounts.get(sender).addToSent(senderCopy);
            } catch (IOException e) {
                serverController.logEvent("❌ Errore salvataggio email per mittente " + sender + ": " + e.getMessage());
                throw e;
            }

            // Step 4: Process recipients' copies
            for (String recipient : recipients) {
                int recipientEmailId = EmailFileManager.getNextId();
                Email recipientCopy = new Email(sender, recipients, email.getSubject(), email.getBody());
                recipientCopy.setId(recipientEmailId);

                try {
                    EmailFileManager.saveEmail(recipientCopy, recipient);
                    accounts.get(recipient).addToInbox(recipientCopy);
                    queueEmail(recipientCopy, recipient);
                    serverController.logEvent("📬 Email consegnata a: " + recipient);
                } catch (IOException e) {
                    serverController.logEvent("❌ Errore salvataggio email per destinatario " + recipient + ": " + e.getMessage());
                    throw e;
                }
            }
        } finally {
            serverLock.writeLock().unlock();
        }
    }



    private void queueEmail(Email email, String recipient) {
        messageQueues.computeIfAbsent(recipient, k -> new ConcurrentLinkedQueue<>())
                .offer(email);
    }

    public List<Email> getNewEmails(String recipient) {
        serverLock.readLock().lock();
        try {
            EmailAccount account = accounts.get(recipient);
            return account != null ? new ArrayList<>(account.getInbox()) : new ArrayList<>();
        } finally {
            serverLock.readLock().unlock();
        }
    }

    public List<Email> retrieveQueuedEmails(String recipient) {
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

    public Email createReplyEmail(int emailId, String requestingUser) {
        Email originalEmail = getEmailById(emailId, requestingUser);
        if (originalEmail == null) {
            return null;
        }

        Email replyEmail = new Email();
        replyEmail.setSender(requestingUser);
        replyEmail.setRecipients(Collections.singletonList(originalEmail.getSender()));
        replyEmail.setSubject("Re: " + originalEmail.getSubject());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String formattedDate = originalEmail.getSentDate().format(formatter);

        String replyBody = String.format("""
        
        
        ----- Messaggio Originale -----
        Da: %s
        Data: %s
        Oggetto: %s
        
        %s""",
                originalEmail.getSender(),
                formattedDate,
                originalEmail.getSubject(),
                originalEmail.getBody());

        replyEmail.setBody(replyBody);
        return replyEmail;
    }

    public Email createReplyAllEmail(int emailId, String requestingUser) {
        Email originalEmail = getEmailById(emailId, requestingUser);
        if (originalEmail == null) {
            return null;
        }

        Set<String> recipients = new HashSet<>(originalEmail.getRecipients());
        recipients.add(originalEmail.getSender());
        recipients.remove(requestingUser);

        Email replyAllEmail = new Email();
        replyAllEmail.setSender(requestingUser);
        replyAllEmail.setRecipients(new ArrayList<>(recipients));
        replyAllEmail.setSubject("Re_ALL: " + originalEmail.getSubject());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String formattedDate = originalEmail.getSentDate().format(formatter);

        String replyBody = String.format("""
        
        
        ----- Messaggio Originale -----
        Da: %s
        Data: %s
        Oggetto: %s
        
        %s""",
                originalEmail.getSender(),
                formattedDate,
                originalEmail.getSubject(),
                originalEmail.getBody());

        replyAllEmail.setBody(replyBody);
        return replyAllEmail;
    }

    public Email createForwardEmail(int emailId, String requestingUser) {
        Email originalEmail = getEmailById(emailId, requestingUser);
        if (originalEmail == null) {
            return null;
        }

        Email forwardEmail = new Email();
        forwardEmail.setSender(requestingUser);
        forwardEmail.setSubject("Fwd: " + originalEmail.getSubject());

        String forwardedContent = String.format("""
        
        ----- Messaggio Inoltrato -----
        Da: %s
        A: %s
        Oggetto: %s
        
        %s""",
                originalEmail.getSender(),
                String.join(", ", originalEmail.getRecipients()),
                originalEmail.getSubject(),
                originalEmail.getBody());

        forwardEmail.setBody(forwardedContent);
        return forwardEmail;
    }

    public boolean deleteEmail(int emailId, String requestingUser) {
        serverLock.writeLock().lock();
        try {
            EmailAccount account = accounts.get(requestingUser);
            if (account == null) {
                serverController.logEvent("⚠️ Tentativo di eliminazione fallito: account non trovato per " + requestingUser);
                return false;
            }

            boolean deletedFromInbox = account.removeFromInbox(emailId);
            boolean deletedFromSent = account.removeFromSent(emailId);

            if (deletedFromInbox || deletedFromSent) {
                try {
                    EmailFileManager.deleteEmail(emailId, requestingUser);
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }
            serverController.logEvent("⚠️ Email " + emailId + " non trovata per l'utente " + requestingUser);
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

    public Email getEmailById(int emailId, String requestingUser) {
        serverLock.readLock().lock();
        try {
            for (EmailAccount account : accounts.values()) {
                Email email = account.getEmailById(emailId);
                if (email != null) {
                    return email;
                }
            }
            return null;
        } finally {
            serverLock.readLock().unlock();
        }
    }
}