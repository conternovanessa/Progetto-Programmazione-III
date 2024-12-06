package com.emailapp.server.model;

import java.util.Map;

public class ClientPorts {
    private static final Map<String, Integer> CLIENT_PORTS = Map.of(
            "fabiodelia@progetto.com", 801,
            "filippoditto@progetto.com", 904,
            "vanessaconterno@progetto.com", 2808
    );

    public static int getControlPort() {
        return 5000; // Porta principale del server
    }

    public static int getPortForClient(String email) {
        return CLIENT_PORTS.getOrDefault(email, -1);
    }

    public static boolean isValidPort(int port) {
        return CLIENT_PORTS.containsValue(port);
    }

    public static boolean hasAssignedPort(String email) {
        return CLIENT_PORTS.containsKey(email);
    }
}
