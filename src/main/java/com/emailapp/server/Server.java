package com.emailapp.server;

import com.emailapp.server.controller.ServerController;

public class Server {
    private final ServerController serverController;
    private static final int DEFAULT_PORT = 5000;

    public Server(ServerController serverController) {
        this.serverController = serverController;
    }

    public void start() {
        serverController.startServer(DEFAULT_PORT);
    }

    public void stop() {
        serverController.stopServer();
    }
}


