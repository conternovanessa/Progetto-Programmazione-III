package com.emailapp.server.model;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ClientPorts {
    private static final Map<String, Integer> CLIENT_PORTS = Collections.synchronizedMap(loadPortsFromFile());

    private static Map<String, Integer> loadPortsFromFile() {
        Map<String, Integer> ports = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("src/main/resources/emails.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(",");
                if (parts.length == 2) {
                    String email = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    ports.put(email, port);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ports;
    }

    public static synchronized int getControlPort() {
        return 5000;
    }

    public static synchronized int getPortForClient(String email) {
        return CLIENT_PORTS.getOrDefault(email, -1);
    }

    public static synchronized boolean isValidPort(int port) {
        return CLIENT_PORTS.containsValue(port);
    }

    public static synchronized boolean hasAssignedPort(String email) {
        return CLIENT_PORTS.containsKey(email);
    }
}