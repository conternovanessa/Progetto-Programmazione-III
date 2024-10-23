package com.emailapp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.emailapp.client.Client;
import com.emailapp.server.controller.ServerController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class StartApp extends Application {
    private ServerController serverController;

    @Override
    public void start(Stage primaryStage) {
        try {
            // Carica il file FXML per la vista del server
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/emailapp/server/ServerView.fxml"));
            Parent root = loader.load();

            // Ottiene il controller
            serverController = loader.getController();

            // Crea la scena per la vista del server
            Scene scene = new Scene(root, 600, 400);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Email Server");
            primaryStage.show();

            // Leggi gli indirizzi email e avvia i client
            List<String> emailAddresses = readEmailAddresses();
            for (String email : emailAddresses) {
                startClient(email);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Errore durante l'avvio dell'applicazione: " + e.getMessage());
            Platform.exit();
        }
    }

    private void startClient(String email) {
        Platform.runLater(() -> {
            try {
                Client client = new Client();
                client.setEmailAddress(email);
                client.start(new Stage());
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Errore durante l'avvio del client per " + email + ": " + e.getMessage());
            }
        });
    }

    private List<String> readEmailAddresses() {
        List<String> emails = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream("/emails.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            if (is == null) {
                System.err.println("Il file emails.txt non è stato trovato.");
                return emails;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                emails.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return emails;
    }

    @Override
    public void stop() {
        if (serverController != null && serverController.isRunning()) {
            serverController.handleStopServer();
        }
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}