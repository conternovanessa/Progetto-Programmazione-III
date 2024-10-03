package com.emailapp.server.controller;

import com.emailapp.server.model.MailServer;
import com.emailapp.client.model.Email;
import com.emailapp.server.view.ServerViewController;
import javafx.application.Platform;

import java.util.List;

public class ServerController {
    private MailServer mailServer;
    private ServerViewController viewController;

    public ServerController() {
        this.mailServer = new MailServer();
    }

    public void setViewController(ServerViewController viewController) {
        this.viewController = viewController;
    }

    public void handleClientRequest(String request, Object data) {
        switch (request) {
            case "SEND_EMAIL":
                handleSendEmail((Email) data);
                break;
            case "FETCH_NEW_EMAILS":
                handleFetchNewEmails((String) data);
                break;
            case "DELETE_EMAIL":
                handleDeleteEmail((String) data);
                break;
            default:
                logEvent("Unknown request: " + request);
        }
    }

    private void handleSendEmail(Email email) {
        boolean success = mailServer.deliverEmail(email);
        logEvent("Email sent from " + email.getSender() + " to " + email.getRecipients());
        // Respond to client with success status
    }

    private void handleFetchNewEmails(String emailAddress) {
        List<Email> newEmails = mailServer.getNewEmails(emailAddress);
        logEvent("Fetched " + newEmails.size() + " new emails for " + emailAddress);
        // Send newEmails to client
    }

    private void handleDeleteEmail(String emailId) {
        boolean success = mailServer.deleteEmail(emailId);
        logEvent("Deleted email with ID: " + emailId);
        // Respond to client with success status
    }

    public void logEvent(String event) {
        Platform.runLater(() -> viewController.addLogEntry(event));
    }
}