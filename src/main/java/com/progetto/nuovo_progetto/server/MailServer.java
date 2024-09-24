package com.progetto.nuovo_progetto.server;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.progetto.nuovo_progetto.server.controller.ServerController;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MailServer extends Application {
    private static final int PORT = 5000;
    private ServerController controller;
    private ExecutorService executorService;

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/progetto/nuovo_progetto/server/ServerView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        primaryStage.setTitle("Mail Server");
        primaryStage.setScene(new Scene(root, 400, 300));
        primaryStage.show();

        startServer();
    }

    private void startServer() {
        executorService = Executors.newCachedThreadPool();
        new Thread(this::acceptClients).start();
    }

    private void acceptClients() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            controller.handleServerStarted(PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            String clientAddress = clientSocket.getInetAddress().getHostAddress();
            controller.handleClientConnection(clientAddress);
            // Qui gestirai la comunicazione con il client
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}