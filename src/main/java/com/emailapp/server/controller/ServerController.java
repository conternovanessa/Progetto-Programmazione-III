package com.emailapp.server.controller;

import com.emailapp.server.Server;
import com.emailapp.server.view.ServerViewController;
import javafx.application.Platform;

public class ServerController {

    private final ServerViewController viewController;
    private Server server;
    private boolean isServerRunning;

    public ServerController(ServerViewController viewController) {
        this.viewController = viewController;
        this.isServerRunning = false;
    }

    public void logEvent(String message) {
        if (viewController != null) {
            Platform.runLater(() -> {
                try {
                    viewController.addLogEntry(message);
                } catch (Exception e) {
                    System.err.println("Error logging event: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } else {
            System.err.println("viewController is null! Cannot log event: " + message);
        }
    }

    public void startServer() {
        if (!isServerRunning) {
            server = new Server(this);
            server.start();
            isServerRunning = true;
            viewController.updateServerStatus(true);
        }
    }

    public void stopServer() {
        if (isServerRunning && server != null) {
            server.stop();
            isServerRunning = false;
            viewController.updateServerStatus(false);
        }
    }

    public boolean isServerRunning() {
        return isServerRunning;
    }

}