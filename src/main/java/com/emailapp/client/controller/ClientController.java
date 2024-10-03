package com.emailapp.client.controller;

import com.emailapp.client.model.Email;
import com.emailapp.client.model.Mailbox;
import com.emailapp.common.NetworkUtils;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientController {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    private final Mailbox mailbox;
    private final ExecutorService executorService;
    private final BooleanProperty connectedProperty;

    public ClientController(String emailAddress) {
        this.mailbox = new Mailbox(emailAddress);
        this.executorService = Executors.newCachedThreadPool();
        this.connectedProperty = new SimpleBooleanProperty(false);

        startConnectionChecker();
    }

    public void sendEmail(Email email) {
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "SEND_EMAIL");
                NetworkUtils.sendObject(socket, email);
                String response = (String) NetworkUtils.receiveObject(socket);
                if ("SUCCESS".equals(response)) {
                    Platform.runLater(() -> {
                        mailbox.addSentEmail(email);
                    });
                } else {
                    Platform.runLater(() -> {
                        // Notifica l'utente dell'errore
                    });
                }
            } catch (Exception e) {
                handleConnectionError(e);
            }
        });
    }

    public void fetchNewEmails() {
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "FETCH_NEW_EMAILS");
                NetworkUtils.sendObject(socket, mailbox.getEmailAddress());
                @SuppressWarnings("unchecked")
                List<Email> newEmails = (List<Email>) NetworkUtils.receiveObject(socket);
                Platform.runLater(() -> {
                    for (Email email : newEmails) {
                        mailbox.addReceivedEmail(email);
                    }
                    // Notifica l'utente dei nuovi messaggi
                });
            } catch (Exception e) {
                handleConnectionError(e);
            }
        });
    }

    public void deleteEmail(Email email) {
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "DELETE_EMAIL");
                NetworkUtils.sendObject(socket, email.getId());
                String response = (String) NetworkUtils.receiveObject(socket);
                if ("SUCCESS".equals(response)) {
                    Platform.runLater(() -> {
                        mailbox.removeEmail(email);
                    });
                } else {
                    Platform.runLater(() -> {
                        // Notifica l'utente dell'errore
                    });
                }
            } catch (Exception e) {
                handleConnectionError(e);
            }
        });
    }

    public ObservableList<Email> getEmails() {
        return mailbox.getEmails();
    }

    public String getEmailAddress() {
        return mailbox.getEmailAddress();
    }

    public BooleanProperty connectedProperty() {
        return connectedProperty;
    }

    private void startConnectionChecker() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                    NetworkUtils.sendObject(socket, "PING");
                    String response = (String) NetworkUtils.receiveObject(socket);
                    boolean isConnected = "PONG".equals(response);
                    Platform.runLater(() -> connectedProperty.set(isConnected));
                } catch (Exception e) {
                    Platform.runLater(() -> connectedProperty.set(false));
                }
                try {
                    Thread.sleep(5000); // Controlla la connessione ogni 5 secondi
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private void handleConnectionError(Exception e) {
        Platform.runLater(() -> {
            connectedProperty.set(false);
            // Notifica l'utente del problema di connessione
        });
    }

    public void shutdown() {
        executorService.shutdownNow();
    }
}
