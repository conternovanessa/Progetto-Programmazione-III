package com.emailapp.client.controller;

import com.emailapp.client.model.Email;
import com.emailapp.client.model.Mailbox;
import com.emailapp.common.NetworkUtils;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;

import java.io.IOException;
import java.net.Socket;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientController {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    private final Mailbox mailbox;
    private static ExecutorService executorService = null;
    private final BooleanProperty connectedProperty;

    public ClientController(String emailAddress) {
        this.mailbox = new Mailbox(emailAddress);
        this.executorService = Executors.newCachedThreadPool();
        this.connectedProperty = new SimpleBooleanProperty(false);
        startConnectionChecker();
    }

    public void sendEmail(Email email) {
        if (!isConnected()) {
            showServerClosedAlert();
            return;
        }

        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "SEND_EMAIL");
                NetworkUtils.sendObject(socket, email);
                String response = (String) NetworkUtils.receiveObject(socket);
                if ("SUCCESS".equals(response)) {
                    Platform.runLater(() -> mailbox.addSentEmail(email));
                } else {
                    Platform.runLater(() -> showErrorAlert("Send Error", "Failed to send email"));
                }
            } catch (ConnectException e) {
                Platform.runLater(this::showServerClosedAlert);
            } catch (Exception e) {
                handleConnectionError(e);
            }
        });
    }

    public void fetchNewEmails() {
        if (!isConnected()) {
            showServerClosedAlert();
            return;
        }

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
                    showInfoAlert("New Emails", "Received " + newEmails.size() + " new email(s)");
                });
            } catch (ConnectException e) {
                Platform.runLater(this::showServerClosedAlert);
            } catch (Exception e) {
                handleConnectionError(e);
            }
        });
    }

    public void deleteEmail(Email email) {
        if (!isConnected()) {
            showServerClosedAlert();
            return;
        }

        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "DELETE_EMAIL");
                NetworkUtils.sendObject(socket, email.getId());
                String response = (String) NetworkUtils.receiveObject(socket);
                if ("SUCCESS".equals(response)) {
                    Platform.runLater(() -> mailbox.removeEmail(email));
                } else {
                    Platform.runLater(() -> showErrorAlert("Delete Error", "Failed to delete email"));
                }
            } catch (ConnectException e) {
                Platform.runLater(this::showServerClosedAlert);
            } catch (Exception e) {
                handleConnectionError(e);
            }
        });
    }

    public void checkConnection() {
        executorService.submit(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                NetworkUtils.sendObject(socket, "PING");
                String response = (String) NetworkUtils.receiveObject(socket);
                boolean isConnected = "PONG".equals(response);
                Platform.runLater(() -> connectedProperty.set(isConnected));
            } catch (Exception e) {
                Platform.runLater(() -> connectedProperty.set(false));
            }
        });
    }

    public ObservableList<Email> getEmails() {
        return mailbox.getAllEmails();
    }

    public String getEmailAddress() {
        return mailbox.getEmailAddress();
    }

    public BooleanProperty connectedProperty() {
        return connectedProperty;
    }

    public boolean isConnected() {
        return connectedProperty.get();
    }

    private void handleConnectionError(Exception e) {
        Platform.runLater(() -> {
            connectedProperty.set(false);
            showErrorAlert("Connection Error", "Failed to connect to the server: " + e.getMessage());
        });
    }

    private void showServerClosedAlert() {
        showErrorAlert("Server Closed", "The server is currently closed. Please try again later.");
    }

    private void showErrorAlert(String title, String content) {
        showAlert(Alert.AlertType.ERROR, title, content);
    }

    private void showInfoAlert(String title, String content) {
        showAlert(Alert.AlertType.INFORMATION, title, content);
    }

    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void startConnectionChecker() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                checkConnection();
                try {
                    Thread.sleep(5000); // Check connection every 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    public static void shutdown() {
        // This method should be called when the application is closing
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}