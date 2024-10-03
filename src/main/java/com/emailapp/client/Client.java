package com.emailapp.client;

import com.emailapp.client.controller.ClientController;
import com.emailapp.client.view.ClientViewController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Client extends Application {

    private String emailAddress;

    public Client() {
        // Costruttore vuoto necessario per JavaFX
    }

    public Client(String emailAddress) {
        this.emailAddress = emailAddress;
    }
    @Override
    public void start(Stage primaryStage) {
        try {
            List<String> emailAddresses = readEmailAddresses();
            for (String email : emailAddresses) {
                openClientWindow(email);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openClientWindow(String email) {
        try {
            ClientController clientController = new ClientController(email);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/emailapp/client/ClientView.fxml"));
            Parent root = loader.load();
            ClientViewController viewController = loader.getController();

            viewController.setClientController(clientController);

            Stage stage = new Stage();
            Scene scene = new Scene(root, 800, 600);
            stage.setScene(scene);
            stage.setTitle("Email Client - " + email);
            stage.show();

            clientController.fetchNewEmails();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> readEmailAddresses() throws IOException {
        List<String> emails = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream("/emails.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                emails.add(line.trim());
            }
        }
        return emails;
    }

}