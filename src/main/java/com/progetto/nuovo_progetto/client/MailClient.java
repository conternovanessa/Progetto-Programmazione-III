package com.progetto.nuovo_progetto.client;

import com.progetto.nuovo_progetto.client.controller.ClientController;
import com.progetto.nuovo_progetto.client.model.ClientModel;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MailClient extends Application {
    private String emailAddress;

    public MailClient(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/progetto/nuovo_progetto/client/ClientView.fxml"));
        Parent root = loader.load();
        ClientController controller = loader.getController();
        ClientModel model = new ClientModel(emailAddress);
        controller.setModel(model);

        primaryStage.setTitle("Mail Client - " + emailAddress);
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();
    }
}