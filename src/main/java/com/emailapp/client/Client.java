package com.emailapp.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.emailapp.client.controller.ClientController;
import com.emailapp.client.view.ClientViewController;

public class Client extends Application {

    private String emailAddress;

    public Client() {
        // Costruttore vuoto necessario per JavaFX
    }

    public Client(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        ClientController clientController = new ClientController(emailAddress);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/emailapp/client/ClientView.fxml"));
        Parent root = loader.load();
        ClientViewController viewController = loader.getController();

        viewController.setClientController(clientController);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Email Client - " + emailAddress);
        primaryStage.show();

        // Aggiorna lo stato della connessione all'avvio
        clientController.checkConnection();
    }

    @Override
    public void stop() {
        // Gestione della chiusura del client
        try {
            ClientController.shutdown();
        } catch (Exception e) {
            e.printStackTrace(); // Log dell'errore
        }
    }
}
