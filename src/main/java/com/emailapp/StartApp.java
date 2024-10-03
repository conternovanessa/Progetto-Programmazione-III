package com.emailapp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import com.emailapp.server.Server;
import com.emailapp.client.Client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class StartApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Avvia il server
        startServer();

        // Leggi gli indirizzi email e avvia i client
        List<String> emailAddresses = readEmailAddresses();
        for (String email : emailAddresses) {
            startClient(email);
        }
    }

    private void startServer() {
        Platform.runLater(() -> {
            try {
                new Server().start(new Stage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Attendi un po' per assicurarti che il server sia avviato prima dei client
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startClient(String email) {
        Platform.runLater(() -> {
            try {
                new Client(email).start(new Stage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<String> readEmailAddresses() {
        List<String> emails = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream("/emails.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                emails.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return emails;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
