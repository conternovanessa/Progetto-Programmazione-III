package com.emailapp.client;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.util.*;

public class ClientManager {
    private static ClientManager instance;
    private final Map<String, Stage> activeClients;
    private final Map<String, Client> clientInstances;
    private final Set<String> registeredEmails;
    private RecentClientsWindow recentClientsWindow;

    private ClientManager() {
        activeClients = new HashMap<>();
        clientInstances = new HashMap<>();
        registeredEmails = new HashSet<>();
        recentClientsWindow = new RecentClientsWindow(this);
    }

    public static ClientManager getInstance() {
        if (instance == null) {
            instance = new ClientManager();
        }
        return instance;
    }

    public void registerClient(String email, Stage stage, Client client) {
        activeClients.put(email, stage);
        clientInstances.put(email, client);
        registeredEmails.add(email);
    }

    public void removeClient(String email) {
        activeClients.remove(email);
        clientInstances.remove(email);
    }

    public void reopenClient(String email) {
        if (isClientActive(email)) {
            Stage existingStage = getStage(email);
            existingStage.toFront();
            return;
        }

        try {
            Client client = new Client();
            client.setEmailAddress(email);
            client.start(new Stage());
        } catch (Exception e) {
            showError("Errore", "Impossibiità di riaprire il client:  " + email);
        }
    }

    public void clearHistory() {
        registeredEmails.removeIf(email -> !isClientActive(email));
    }

    public void showRecentClientsWindow() {
        recentClientsWindow.show();
    }

    private void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    public boolean isClientActive(String email) {
        return activeClients.containsKey(email);
    }

    public Stage getStage(String emailAddress) {
        return activeClients.get(emailAddress);
    }

    public Set<String> getRegisteredEmails() {
        return new HashSet<>(registeredEmails);
    }
}