module com.progetto.nuovo_progetto {
    requires javafx.controls;
    requires javafx.fxml;

    exports com.progetto.nuovo_progetto;
    exports com.progetto.nuovo_progetto.client;
    exports com.progetto.nuovo_progetto.server;

    // Aggiungi questa riga per esportare il pacchetto del controller
    exports com.progetto.nuovo_progetto.client.controller;

    opens com.progetto.nuovo_progetto to javafx.fxml;
    opens com.progetto.nuovo_progetto.client to javafx.fxml;
    opens com.progetto.nuovo_progetto.server to javafx.fxml;

    // Aggiungi questa riga per aprire il pacchetto del controller a javafx.fxml
    opens com.progetto.nuovo_progetto.client.controller to javafx.fxml;
}