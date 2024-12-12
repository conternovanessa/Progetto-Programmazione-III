package com.emailapp.client;

import com.emailapp.StartApp;
import com.emailapp.client.controller.ClientController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Client extends Application {
    private String emailAddress;
    private ClientController controller;

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/emailapp/client/ClientView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        controller.setEmailAddress(emailAddress);

        Scene scene = new Scene(root, 600, 400);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Email Client - " + emailAddress);

        primaryStage.setOnCloseRequest(e -> {
            shutdown();
            StartApp.getInstance().removeClient(emailAddress);
        });

        StartApp.getInstance().registerClient(emailAddress, primaryStage);
        primaryStage.show();
    }

    private void shutdown() {
        if (controller != null) {
            controller.shutdown();
        }
    }
}
