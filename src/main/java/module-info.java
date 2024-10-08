module com.emailapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;

    opens com.emailapp to javafx.fxml;
    opens com.emailapp.server.view to javafx.fxml;
    opens com.emailapp.client.view to javafx.fxml;
    opens com.emailapp.client.model to com.google.gson;

    exports com.emailapp;
    exports com.emailapp.server;
    exports com.emailapp.client;
    exports com.emailapp.client.model;
}