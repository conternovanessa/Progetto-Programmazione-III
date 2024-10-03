package com.emailapp.server;
import com.emailapp.common.NetworkUtils;
import com.emailapp.server.controller.ServerController;
import com.emailapp.server.view.ServerViewController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server extends Application {
    private static final int PORT = 5000;
    private ServerController serverController;
    private ExecutorService executorService;

    @Override
    public void start(Stage primaryStage) throws Exception {
        serverController = new ServerController();
        executorService = Executors.newCachedThreadPool();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/emailapp/server/ServerView.fxml"));
        Parent root = loader.load();
        ServerViewController viewController = loader.getController();

        serverController.setViewController(viewController);
        viewController.setServerController(serverController);

        primaryStage.setTitle("Email Server");
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();

        startServer();
    }

    private void startServer() {
        executorService.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                serverController.logEvent("Server started on port " + PORT);
                while (!Thread.currentThread().isInterrupted()) {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                }
            } catch (IOException e) {
                serverController.logEvent("Server error: " + e.getMessage());
            }
        });
    }

    private void handleClient(Socket clientSocket) {
        executorService.submit(() -> {
            try {
                // Read request and data from client
                String request = (String) NetworkUtils.receiveObject(clientSocket);
                Object data = NetworkUtils.receiveObject(clientSocket);

                serverController.handleClientRequest(request, data);

                // Send response back to client
                // NetworkUtils.sendObject(clientSocket, response);

                clientSocket.close();
            } catch (Exception e) {
                serverController.logEvent("Error handling client: " + e.getMessage());
            }
        });
    }

    @Override
    public void stop() {
        executorService.shutdownNow();
    }

}