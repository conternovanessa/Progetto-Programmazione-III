module com.emailapp {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.emailapp to javafx.fxml;
    opens com.emailapp.server to javafx.fxml;
    opens com.emailapp.client to javafx.fxml;
    opens com.emailapp.server.view to javafx.fxml;
    opens com.emailapp.client.controller to javafx.fxml; // Aggiungi questa riga

    exports com.emailapp;
    exports com.emailapp.server;
    exports com.emailapp.client;
    exports com.emailapp.common;
    exports com.emailapp.server.model;
    exports com.emailapp.client.model;
    exports com.emailapp.client.controller;
    exports com.emailapp.server.controller;
    exports com.emailapp.server.view;
}