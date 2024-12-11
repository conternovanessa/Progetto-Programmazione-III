package com.emailapp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Scene;
import com.emailapp.client.Client;
import com.emailapp.client.ClientManager;
import com.emailapp.server.Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class StartApp extends Application {
    private Server server;
    private ClientManager clientManager;

    @Override
    public void start(Stage primaryStage) {
        try {
            // Inizializza il gestore dei client
            clientManager = ClientManager.getInstance();

            // Crea e avvia il server
            server = new Server();
            server.start(primaryStage);

            // Avvia i client dalle email predefinite
            List<String> emailAddresses = readEmailAddresses();
            for (String email : emailAddresses) {
                startClient(email);
            }

            // Crea il pulsante per i client recenti
            createRecentClientsButton();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Errore durante l'avvio dell'applicazione: " + e.getMessage());
            Platform.exit();
        }
    }

    private void createRecentClientsButton() {
        Button recentButton = new Button("Client recenti");
        recentButton.setStyle("-fx-font-size: 14px; -fx-min-width: 120px;");
        recentButton.setOnAction(e -> clientManager.showRecentClientsWindow());

        Stage buttonStage = new Stage();
        buttonStage.initModality(Modality.NONE);
        buttonStage.setAlwaysOnTop(true);
        buttonStage.setX(10);
        buttonStage.setY(10);
        buttonStage.setTitle("Client recenti");

        VBox buttonBox = new VBox(recentButton);
        buttonBox.setPadding(new Insets(5));
        Scene buttonScene = new Scene(buttonBox);
        buttonStage.setScene(buttonScene);
        buttonStage.show();
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
                String[] parts = line.trim().split(",");
                if (parts.length >= 1) {
                    emails.add(parts[0].trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return emails;
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}