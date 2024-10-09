package com.emailapp.server;

public class Server {
    private final ServerController serverController;

    public Server(ServerController serverController) {
        this.serverController = serverController;
    }

    public void start() {
        serverController.startServer(5000);
    }

    public void stop() {
        serverController.stopServer();
    }
}
