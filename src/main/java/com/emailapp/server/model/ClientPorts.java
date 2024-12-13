package com.emailapp.server.model;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ClientPorts {
    private static final Map<String, Integer> CLIENT_PORTS = new ConcurrentHashMap<>();
    private static final ReentrantReadWriteLock portsLock = new ReentrantReadWriteLock(true);
    private static final long LOCK_TIMEOUT = 2000; // 2 secondi timeout
    private static final Object LOAD_LOCK = new Object();

    private static Map<String, Integer> loadPortsFromFile() {
        synchronized(LOAD_LOCK) {
            Map<String, Integer> ports = new ConcurrentHashMap<>();
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
                throw new RuntimeException("Errore nel caricamento delle porte", e);
            }
            return ports;
        }
    }

    public static int getPortForClient(String email) {
        try {
            if (!portsLock.readLock().tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout durante l'accesso alla porta del client");
            }
            try {
                return CLIENT_PORTS.computeIfAbsent(email, k -> {
                    Map<String, Integer> loadedPorts = loadPortsFromFile();
                    return loadedPorts.getOrDefault(k, -1);
                });
            } finally {
                portsLock.readLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operazione interrotta durante l'accesso alla porta", e);
        }
    }
}

