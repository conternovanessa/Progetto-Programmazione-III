package com.emailapp.server;

import com.emailapp.server.controller.ServerController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Server extends Application {
    private ServerController controller;
    private static final int DEFAULT_PORT = 5000;
    private final ReentrantLock serverLock = new ReentrantLock(true);
    private static final long LOCK_TIMEOUT = 3000;
    private volatile boolean isShuttingDown = false;
    private static Server instance;

    public Server() {
        instance = this;
    }

    public static Server getInstance() {
        return instance;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        if (isShuttingDown) return;

        try {
            if (!serverLock.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout durante l'avvio del server");
            }

            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/emailapp/server/ServerView.fxml"));
                Parent root = loader.load();
                controller = loader.getController();

                Scene scene = new Scene(root, 600, 400);
                primaryStage.setScene(scene);
                primaryStage.setTitle("Email Server");

                primaryStage.setOnCloseRequest(e -> {
                    e.consume();
                    performCleanShutdown(primaryStage);
                });

                startServer();
                primaryStage.show();
            } finally {
                if (serverLock.isHeldByCurrentThread()) {
                    serverLock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operazione interrotta durante l'avvio del server");
        }
    }

    private void performCleanShutdown(Stage primaryStage) {
        if (isShuttingDown) return;

        isShuttingDown = true;
        try {
            stopServer();

            // Ensure all resources are released
            if (controller != null) {
                controller.shutdown(); // Add this method to ServerController
            }

            Platform.runLater(() -> {
                try {
                    primaryStage.close();
                } finally {
                    Platform.exit();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startServer() {
        if (isShuttingDown) return;

        try {
            if (!serverLock.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout durante l'avvio del server");
            }
            try {
                if (controller != null) {
                    controller.startServer(DEFAULT_PORT);
                }
            } finally {
                if (serverLock.isHeldByCurrentThread()) {
                    serverLock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operazione interrotta durante l'avvio del server");
        }
    }

    public void stopServer() {
        if (isShuttingDown) return;

        try {
            if (!serverLock.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                return;
            }
            try {
                if (controller != null) {
                    controller.stopServer();
                }
            } finally {
                if (serverLock.isHeldByCurrentThread()) {
                    serverLock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        performCleanShutdown(null);
    }
}
