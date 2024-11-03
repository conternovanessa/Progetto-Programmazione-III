
module com.emailapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;

    opens com.emailapp to javafx.fxml;
    opens com.emailapp.server to javafx.fxml;
    opens com.emailapp.client to javafx.fxml;

    exports com.emailapp;
    exports com.emailapp.server;
    exports com.emailapp.client;
    exports com.emailapp.client.model;
    opens com.emailapp.client.model to javafx.fxml;
    exports com.emailapp.client.controller;
    opens com.emailapp.client.controller to javafx.fxml;
    exports com.emailapp.util;
    opens com.emailapp.util to javafx.fxml;
    exports com.emailapp.spam;
    opens com.emailapp.spam to javafx.fxml;
    exports com.emailapp.server.model;
    opens com.emailapp.server.model to javafx.fxml;
    exports com.emailapp.server.controller;
    opens com.emailapp.server.controller to javafx.fxml;

}
