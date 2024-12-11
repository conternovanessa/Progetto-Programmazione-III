package com.emailapp;

import com.emailapp.client.Client;
import com.emailapp.server.Server;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class StartApp extends Application {
    private static StartApp instance;
    private final Map<String, Stage> activeClients = new HashMap<>();
    private final Set<String> registeredEmails = new HashSet<>();
    private final Object lock = new Object();
    private Stage recentClientsStage;
    private ListView<String> clientsList;

    // Metodi di inizializzazione principale
    @Override
    public void start(Stage primaryStage) {
        instance = this;
        initializeRecentClientsWindow();
        startServer();
        startInitialClients();
        showRecentClientsButton();
    }

    public static StartApp getInstance() {
        return instance;
    }

    // Gestione Server
    private void startServer() {
        try {
            Server server = new Server();
            Stage serverStage = new Stage();
            server.start(serverStage);
        } catch (Exception e) {
            handleError("Errore Server", "Impossibile avviare il server: " + e.getMessage());
        }
    }

    // Gestione Client
    private void startInitialClients() {
        List<String> emailAddresses = readEmailAddresses();
        for (String email : emailAddresses) {
            startClient(email);
        }
    }

    public void startClient(String email) {
        synchronized(lock) {
            if (isClientActive(email)) {
                activeClients.get(email).toFront();
                return;
            }

            Platform.runLater(() -> {
                try {
                    Client client = new Client();
                    client.setEmailAddress(email);
                    Stage clientStage = new Stage();
                    client.start(clientStage);
                } catch (Exception e) {
                    handleError("Errore Client", "Impossibile avviare il client per " + email);
                }
            });
        }
    }

    // Gestione Registrazione Client
    public void registerClient(String email, Stage stage) {
        synchronized(lock) {
            activeClients.put(email, stage);
            registeredEmails.add(email);
            updateClientsList();
        }
    }

    public void removeClient(String email) {
        synchronized(lock) {
            activeClients.remove(email);
            updateClientsList();
        }
    }

    public boolean isClientActive(String email) {
        synchronized(lock) {
            return activeClients.containsKey(email);
        }
    }

    // Gestione Finestra Client Recenti
    private void initializeRecentClientsWindow() {
        recentClientsStage = new Stage();
        recentClientsStage.setTitle("Client Recenti");

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        Label titleLabel = new Label("Client Disponibili");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        clientsList = new ListView<>();

        Button refreshButton = new Button("Aggiorna Lista");
        refreshButton.setOnAction(e -> updateClientsList());

        Button openSelectedButton = new Button("Apri Client Selezionato");
        openSelectedButton.setOnAction(e -> {
            String selectedEmail = clientsList.getSelectionModel().getSelectedItem();
            if (selectedEmail != null) {
                startClient(selectedEmail);
            }
        });

        Button clearHistoryButton = new Button("Pulisci Storia");
        clearHistoryButton.setOnAction(e -> {
            clearHistory();
            updateClientsList();
        });

        root.getChildren().addAll(
                titleLabel,
                clientsList,
                refreshButton,
                openSelectedButton,
                clearHistoryButton
        );

        Scene scene = new Scene(root, 300, 400);
        recentClientsStage.setScene(scene);
        recentClientsStage.setOnShowing(e -> updateClientsList());
    }

    private void showRecentClientsButton() {
        Button recentButton = new Button("Client Recenti");
        recentButton.setStyle("-fx-font-size: 14px; -fx-min-width: 120px;");
        recentButton.setOnAction(e -> showRecentClientsWindow());

        Stage buttonStage = new Stage();
        buttonStage.initModality(Modality.NONE);
        buttonStage.setAlwaysOnTop(true);
        buttonStage.setX(10);
        buttonStage.setY(10);

        VBox buttonBox = new VBox(recentButton);
        buttonBox.setPadding(new Insets(5));
        buttonStage.setScene(new Scene(buttonBox));
        buttonStage.show();
    }

    private void showRecentClientsWindow() {
        if (!recentClientsStage.isShowing()) {
            updateClientsList();
            recentClientsStage.show();
        } else {
            recentClientsStage.toFront();
        }
    }

    private void updateClientsList() {
        Platform.runLater(() -> {
            clientsList.getItems().clear();
            synchronized(lock) {
                registeredEmails.stream()
                        .filter(email -> !isClientActive(email))
                        .forEach(clientsList.getItems()::add);
            }
        });
    }

    public void clearHistory() {
        synchronized(lock) {
            registeredEmails.removeIf(email -> !isClientActive(email));
        }
    }

    // Gestione File
    private List<String> readEmailAddresses() {
        List<String> emails = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream("/emails.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            if (is == null) {
                handleError("File Error", "Il file emails.txt non è stato trovato.");
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
            handleError("File Error", "Errore nella lettura del file emails.txt");
        }
        return emails;
    }

    // Gestione Errori
    private void handleError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show();
        });
    }

    // Chiusura Applicazione
    @Override
    public void stop() {
        synchronized(lock) {
            activeClients.values().forEach(Stage::close);
            activeClients.clear();
            registeredEmails.clear();
        }
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
