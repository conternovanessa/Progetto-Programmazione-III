module com.emailapp {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.emailapp to javafx.fxml;
    opens com.emailapp.server.view to javafx.fxml;
    opens com.emailapp.client.view to javafx.fxml;

    exports com.emailapp;
    exports com.emailapp.server;
    exports com.emailapp.client;
}