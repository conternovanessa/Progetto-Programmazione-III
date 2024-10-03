package com.emailapp;

import javafx.application.Platform;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class StartApp {

    public static void main(String[] args) {
        // Avvia il server
        startServer();

        // Leggi gli indirizzi email e avvia i client
        List<String> emailAddresses = readEmailAddresses();
        for (String email : emailAddresses) {
            startClient(email);
        }
    }

    private static void startServer() {
        new Thread(() -> {
            Platform.startup(() -> {
                try {
                    new com.emailapp.server.Server().start(new javafx.stage.Stage());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }).start();

        // Attendi un po' per assicurarti che il server sia avviato prima dei client
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void startClient(String email) {
        new Thread(() -> {
            Platform.startup(() -> {
                try {
                    new com.emailapp.client.Client(email).start(new javafx.stage.Stage());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }).start();
    }

    private static List<String> readEmailAddresses() {
        List<String> emails = new ArrayList<>();
        try (InputStream is = StartApp.class.getResourceAsStream("/emails.txt");
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
}