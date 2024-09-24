package com.progetto.nuovo_progetto;

import com.progetto.nuovo_progetto.server.MailServer;
import com.progetto.nuovo_progetto.client.MailClient;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main extends Application {
    private static final String EMAIL_FILE = "email.txt";
    private static List<String> emailAddresses = new ArrayList<>();

    public static void main(String[] args) {
        loadEmailsFromFile();
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Avvia il server
        Platform.runLater(() -> {
            try {
                new MailServer().start(new Stage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Avvia i client
        for (String email : emailAddresses) {
            Platform.runLater(() -> {
                try {
                    new MailClient(email).start(new Stage());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static void loadEmailsFromFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(EMAIL_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String email = line.trim();
                if (!email.isEmpty()) {
                    emailAddresses.add(email);
                }
            }
        } catch (IOException e) {
            System.err.println("Errore nella lettura del file email.txt: " + e.getMessage());
        }
    }
}