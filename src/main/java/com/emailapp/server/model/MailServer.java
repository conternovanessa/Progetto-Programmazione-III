package com.emailapp.server.model;

import com.emailapp.client.model.Email;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MailServer {
    private Map<String, EmailAccount> accounts;

    public MailServer() {
        this.accounts = new ConcurrentHashMap<>();
    }

    public void addAccount(String email) {
        accounts.putIfAbsent(email, new EmailAccount(email));
    }

    public void deliverEmail(Email email) {
        // Implementa la logica per consegnare l'email agli account destinatari
    }

    // Altri metodi per gestire le operazioni del server
}