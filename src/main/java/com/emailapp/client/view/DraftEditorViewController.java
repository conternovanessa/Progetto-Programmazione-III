package com.emailapp.client.view;

import com.emailapp.client.controller.ClientController;
import com.emailapp.client.model.EmailDraft;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class DraftEditorViewController {
    @FXML private TextField recipientsField;
    @FXML private TextField subjectField;
    @FXML private TextArea bodyArea;

    private EmailDraft draft;
    private ClientController clientController;

    public void setDraft(EmailDraft draft) {
        this.draft = draft;
        recipientsField.textProperty().bindBidirectional(draft.recipientsProperty());
        subjectField.textProperty().bindBidirectional(draft.subjectProperty());
        bodyArea.textProperty().bindBidirectional(draft.bodyProperty());
    }

    public void setClientController(ClientController clientController) {
        this.clientController = clientController;
    }

    @FXML
    private void handleSend() {
        clientController.sendDraft(draft);
        closeWindow();
    }

    @FXML
    private void handleSave() {
        clientController.updateDraft(draft);
    }

    private void closeWindow() {
        recipientsField.getScene().getWindow().hide();
    }
}