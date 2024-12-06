package com.emailapp.client.model;

import java.util.Map;

public class ClientPorts {
    private static final Map<String, Integer> CLIENT_PORTS = Map.of(
            "fabiodelia@progetto.com", 5001,
            "filippoditto@progetto.com", 5002,
            "vanessaconterno@progetto.com", 5003
    );

    public static int getControlPort() {
        return 5000; // Main server control port
    }

    public static int getPortForClient(String email) {
        return CLIENT_PORTS.getOrDefault(email, 5000);
    }
}