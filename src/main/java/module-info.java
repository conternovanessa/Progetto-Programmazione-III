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

}
