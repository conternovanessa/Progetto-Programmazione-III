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

    public void start(Stage primaryStage) throws Exception {
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
                    stopServer();
                    Platform.exit();
                });

                startServer();
                primaryStage.show();
            } finally {
                serverLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operazione interrotta durante l'avvio del server");
        }
    }

    public void startServer() {
        try {
            if (!serverLock.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout durante l'avvio del server");
            }
            try {
                controller.startServer(DEFAULT_PORT);
            } finally {
                serverLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operazione interrotta durante l'avvio del server");
        }
    }

    public void stopServer() {
        try {
            if (!serverLock.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout durante l'arresto del server");
            }
            try {
                if (controller != null) {
                    controller.stopServer();
                }
            } finally {
                serverLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operazione interrotta durante l'arresto del server");
        }
    }

    @Override
    public void stop() {
        stopServer();
    }

    public ServerController getController() {
        return controller;
    }
}
