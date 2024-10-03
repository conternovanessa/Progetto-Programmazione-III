package com.emailapp.server.controller;
import com.emailapp.server.model.MailServer;

public class ServerController {
    private MailServer mailServer;

    public ServerController() {
        this.mailServer = new MailServer();
    }

    public void handleClientRequest(String request) {
        // Implementa la logica per gestire le richieste dei client
    }

    public void logEvent(String event) {
        // Implementa la logica per registrare gli eventi nel log del server
    }
}