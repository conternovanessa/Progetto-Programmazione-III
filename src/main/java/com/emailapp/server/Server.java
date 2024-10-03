package com.emailapp.server;

import com.emailapp.server.controller.ServerController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Server extends Application {

    private ServerController serverController;

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/emailapp/server/ServerView.fxml"));
        Parent root = loader.load();
        serverController = loader.getController();

        primaryStage.setTitle("Email Server");
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();

        // Inizializza il server, ma non lo avvia automaticamente
        serverController.initializeServer();
    }

    public static void main(String[] args) {
        launch(args);
    }
}