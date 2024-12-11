package com.emailapp.server;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.emailapp.server.controller.ServerController;
import java.io.IOException;

public class Server {
    // Costanti
    private static final int DEFAULT_PORT = 5000;
    private static final int WINDOW_WIDTH = 600;
    private static final int WINDOW_HEIGHT = 400;

    // Controller del server
    private ServerController serverController;

    /**
     * Avvia il server e la sua interfaccia grafica
     * @param primaryStage Lo stage principale per la GUI del server
     * @throws IOException Se ci sono problemi nel caricamento del FXML
     */
    public void start(Stage primaryStage) throws IOException {
        // Carica il file FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/emailapp/server/ServerView.fxml"));
        Parent root = loader.load();

        // Ottiene il controller
        serverController = loader.getController();

        // Configura e mostra la finestra
        setupStage(primaryStage, root);

        // Avvia il server sulla porta predefinita
        serverController.startServer(DEFAULT_PORT);
    }

    /**
     * Configura lo stage del server
     * @param stage Lo stage da configurare
     * @param root Il root node dell'interfaccia
     */
    private void setupStage(Stage stage, Parent root) {
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        stage.setScene(scene);
        stage.setTitle("Email Server");
        stage.show();
    }

    /**
     * Arresta il server
     */
    public void stop() {
        if (serverController != null) {
            serverController.stopServer();
        }
    }
}