package com.emailapp.client;

import com.emailapp.client.controller.ClientController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Client extends Application {
    private String emailAddress;
    private ClientController controller;

    public Client() {

    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        if (ClientManager.getInstance().isClientActive(emailAddress)) {
            Stage existingStage = ClientManager.getInstance().getStage(emailAddress);
            existingStage.toFront();
            return;
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/emailapp/client/ClientView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        controller.setEmailAddress(emailAddress);

        Scene scene = new Scene(root, 600, 400);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Email Client - " + emailAddress);

        ClientManager.getInstance().registerClient(emailAddress, primaryStage, this);

        primaryStage.setOnCloseRequest(event -> {
            if (controller != null) {
                controller.shutdown();
            }
            ClientManager.getInstance().removeClient(emailAddress);
        });

        primaryStage.show();
        controller.checkConnection();
    }

    public void shutdown() {
        if (controller != null) {
            controller.shutdown();
        }
    }
}