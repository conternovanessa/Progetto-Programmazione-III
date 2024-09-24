package com.progetto.nuovo_progetto.client.controller;

import com.progetto.nuovo_progetto.client.model.ClientModel;
import com.progetto.nuovo_progetto.common.Email;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

public class ClientController {
    @FXML private TableView<Email> emailTableView;
    @FXML private TableColumn<Email, String> senderColumn;
    @FXML private TableColumn<Email, String> subjectColumn;
    @FXML private TableColumn<Email, String> dateColumn;

    private ClientModel model;

    public void initialize() {
        senderColumn.setCellValueFactory(new PropertyValueFactory<>("sender"));
        subjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("sentDate"));
    }

    public void setModel(ClientModel model) {
        this.model = model;
        emailTableView.setItems(model.getInbox());
    }

    @FXML
    private void handleCompose() {
        // Implementa la logica per comporre una nuova email
    }

    @FXML
    private void handleRefresh() {
        fetchEmails();
    }

    private void fetchEmails() {
        try (Socket socket = new Socket("localhost", 5000);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("FETCH_EMAILS");
            out.writeObject(model.getEmailAddress());
            out.flush();

            List<Email> newEmails = (List<Email>) in.readObject();
            for (Email email : newEmails) {
                model.addEmail(email);
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            // Gestisci l'errore, magari mostrando un messaggio all'utente
        }
    }

    // Altri metodi per gestire le azioni dell'utente
}