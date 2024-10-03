package com.emailapp;

import com.emailapp.server.Server;
import com.emailapp.client.Client;
import javafx.application.Application;
import javafx.stage.Stage;

public class StartApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Avvia il server
        Thread serverThread = new Thread(() -> {
            try {
                new Server().start(new Stage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        // Avvia tre client
        for (int i = 0; i < 3; i++) {
            Thread clientThread = new Thread(() -> {
                try {
                    new Client().start(new Stage());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            clientThread.start();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}