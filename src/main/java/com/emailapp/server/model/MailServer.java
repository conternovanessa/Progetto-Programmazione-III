package com.emailapp.server.model;

import com.emailapp.client.model.Email;
import com.emailapp.server.controller.ServerController;
import com.emailapp.util.EmailFileManager;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;


public class MailServer {
    private final Map<String, EmailAccount> accounts;
    private final ServerController serverController;
    private final Object accountLock = new Object();
    private final Object emailLock = new Object();
    private Map<String, Queue<Email>> messageQueues = new ConcurrentHashMap<>();

    public MailServer(ServerController serverController) {
        this.accounts = new HashMap<>();
        this.serverController = serverController;
        loadExistingEmails(); // Aggiungi questa chiamata
    }

    public void loadExistingEmails() {
        try {
            List<String> emailAddresses = EmailFileManager.loadValidEmails();

            for (String emailAddress : emailAddresses) {
                createAccount(emailAddress);

                try {
                    List<Email> emails = EmailFileManager.loadEmails(emailAddress);
                    EmailAccount account = accounts.get(emailAddress);

                    for (Email email : emails) {
                        if (email.getSender().equals(emailAddress)) {
                            account.addToSent(email);
                        } else {
                            account.addToInbox(email);
                        }
                    }
                } catch (IOException e) {
                    serverController.logEvent("Errore nel caricamento delle email per " + emailAddress + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            serverController.logEvent("Errore nel caricamento degli indirizzi email validi: " + e.getMessage());
        }
    }
    public void createAccount(String emailAddress) {
        accounts.putIfAbsent(emailAddress, new EmailAccount(emailAddress));
    }

    public void sendEmail(Email email) {
        synchronized (emailLock) {
            String sender = email.getSender();
            List<String> recipients = email.getRecipients();

            synchronized (accountLock) {
                createAccount(sender);
                recipients.forEach(this::createAccount);
            }

            // Salva una copia dell'email nella cartella sent del mittente
            int sentEmailId = EmailFileManager.getNextId();
            Email senderCopy = new Email(sender, recipients, email.getSubject(), email.getBody());
            senderCopy.setId(sentEmailId);

            try {
                EmailFileManager.saveEmail(senderCopy, sender);
            } catch (IOException e) {
                serverController.logEvent("Errore durante il salvataggio dell'email inviata per " + sender + ": " + e.getMessage());
            }

            // Invia una copia dell'email a ciascun destinatario
            for (String recipient : recipients) {
                int recipientEmailId = EmailFileManager.getNextId();
                Email recipientCopy = new Email(sender, recipients, email.getSubject(), email.getBody());
                recipientCopy.setId(recipientEmailId);

                synchronized (accountLock) {
                    accounts.get(recipient).addToInbox(recipientCopy);
                }

                try {
                    EmailFileManager.saveEmail(recipientCopy, recipient);
                } catch (IOException e) {
                    serverController.logEvent("Errore durante il salvataggio dell'email per " + recipient + ": " + e.getMessage());
                }
            }

            // Queue the email for recipients
            queueEmail(email);
        }
    }


    private Email createEmailCopy(Email original) {
        Email copy = new Email();
        copy.setId(original.getId());
        copy.setSender(original.getSender());
        copy.setRecipients(new ArrayList<>(original.getRecipients()));
        copy.setSubject(original.getSubject());
        copy.setBody(original.getBody());
        copy.setSentDate(original.getSentDate());
        copy.setRead(false);
        return copy;
    }

    public List<Email> getNewEmails(String recipient) {
        synchronized (emailLock) {
            synchronized (accountLock) {
                createAccount(recipient);
                return new ArrayList<>(accounts.get(recipient).getInbox());
            }
        }
    }
    public boolean canAccessEmail(int emailId, String requestingUser) {
        synchronized (emailLock) {
            synchronized (accountLock) {
                for (EmailAccount account : accounts.values()) {
                    if (account.getInbox().stream().anyMatch(email -> email.getId() == emailId) ||
                            account.getSent().stream().anyMatch(email -> email.getId() == emailId)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    public void queueEmail(Email email) {
        for (String recipient : email.getRecipients()) {
            messageQueues.computeIfAbsent(recipient, k -> new ConcurrentLinkedQueue<>())
                    .offer(email);
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
        synchronized (emailLock) {
            synchronized (accountLock) {
                createAccount(recipient);
                EmailAccount account = accounts.get(recipient);
                List<Email> allEmails = new ArrayList<>();
                // Aggiungi sia le email ricevute che quelle inviate
                allEmails.addAll(account.getInbox());
                allEmails.addAll(account.getSent());
                return allEmails;
            }
        }
    }



    public boolean deleteEmail(int emailId, String requestingUser) {
        synchronized (emailLock) {
            synchronized (accountLock) {
                EmailAccount account = accounts.get(requestingUser);
                if (account == null) {
                    serverController.logEvent("⚠️ Tentativo di eliminazione fallito: account non trovato per " + requestingUser);
                    return false;
                }

                boolean deletedFromInbox = account.getInbox().removeIf(email -> email.getId() == emailId);
                boolean deletedFromSent = account.getSent().removeIf(email -> email.getId() == emailId);

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
            }
        }
    }
    public List<Email> getAllEmails() {
        synchronized (emailLock) {
            synchronized (accountLock) {
                List<Email> allEmails = new ArrayList<>();
                for (EmailAccount account : accounts.values()) {
                    allEmails.addAll(account.getInbox());
                    allEmails.addAll(account.getSent());
                }
                return allEmails;
            }
        }
    }

    public List<Email> getEmailsFromSender(String sender) {
        synchronized (emailLock) {
            synchronized (accountLock) {
                EmailAccount account = accounts.get(sender);
                return account != null ? new ArrayList<>(account.getSent()) : new ArrayList<>();
            }
        }
    }

    public List<Email> getEmailsForRecipient(String recipient) {
        synchronized (emailLock) {
            synchronized (accountLock) {
                EmailAccount account = accounts.get(recipient);
                return account != null ? new ArrayList<>(account.getInbox()) : new ArrayList<>();
            }
        }
    }

    public Email getEmailById(int emailId, String requestingUser) {
    synchronized (emailLock) {
        synchronized (accountLock) {
            for (EmailAccount account : accounts.values()) {
                Email email = account.getInbox().stream()
                        .filter(e -> e.getId() == emailId)
                        .findFirst()
                        .orElse(null);

                if (email != null) {
                    return email;
                }

                email = account.getSent().stream()
                        .filter(e -> e.getId() == emailId)
                        .findFirst()
                        .orElse(null);

                if (email != null) {
                    return email;
                }
            }
        }
    }
    return null; // or throw an exception if not found
}}