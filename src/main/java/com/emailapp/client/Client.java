package com.emailapp.client;

import com.emailapp.client.controller.ClientController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Client extends Application {
    private String emailAddress;

    public Client() {
        // Costruttore vuoto necessario per JavaFX
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/emailapp/client/ClientView.fxml"));
        Parent root = loader.load();
        ClientController controller = loader.getController();
        controller.setEmailAddress(emailAddress);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Email Client - " + emailAddress);
        primaryStage.show();

        controller.checkConnection();
    }
}
