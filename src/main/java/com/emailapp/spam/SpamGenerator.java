package com.emailapp.spam;

import com.emailapp.client.model.Email;
import com.emailapp.util.NetworkUtils;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SpamGenerator extends JFrame {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final int DELAY_BETWEEN_EMAILS = 500;
    private static final int CONNECTION_TIMEOUT = 5000;

    private static final List<String> SPAM_SUBJECTS = Arrays.asList(
            "Offerta imperdibile!",
            "Hai vinto!",
            "Occasione unica!",
            "Promozione esclusiva",
            "Sconto speciale solo per te!"
    );

    private static final List<String> SPAM_BODIES = Arrays.asList(
            "Approfitta subito di questa offerta limitata!",
            "Sei stato selezionato per ricevere un premio esclusivo!",
            "Non lasciarti sfuggire questa opportunità unica!",
            "Abbiamo una promozione speciale solo per i nostri clienti più fedeli!",
            "Acquista ora e ricevi uno sconto del 50% su tutti i prodotti!"
    );

    private static final List<String> SPAM_SENDERS = Arrays.asList(
            "offerte@spam.com",
            "promozioni@spam.com",
            "marketing@spam.com",
            "sconti@spam.com",
            "occasioni@spam.com"
    );

    private ScheduledExecutorService scheduler;
    private final Random random = new Random();
    private final JButton toggleButton;
    private List<String> emailAddresses;
    private volatile boolean isRunning;
    private Socket currentSocket;

    public SpamGenerator() {
        super("Spam Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(300, 100);

        toggleButton = new JButton("Start Spamming");
        toggleButton.addActionListener(e -> toggleSpamming());

        setLayout(new FlowLayout());
        add(toggleButton);

        loadEmailAddresses();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                cleanup();
            }
        });
    }

    private void cleanup() {
        stopSpamming();
        if (currentSocket != null && !currentSocket.isClosed()) {
            try {
                currentSocket.close();
            } catch (IOException e) {
                System.err.println("Errore nella chiusura del socket: " + e.getMessage());
            }
        }
    }

    private void loadEmailAddresses() {
        emailAddresses = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream("/emails.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                emailAddresses.add(line.trim());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Errore durante il caricamento degli indirizzi email: " + e.getMessage(), "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleSpamming() {
        if (!isRunning) {
            startSpamming();
            toggleButton.setText("Stop Spamming");
            isRunning = true;
        } else {
            stopSpamming();
            toggleButton.setText("Start Spamming");
            isRunning = false;
        }
    }

    private void startSpamming() {
        if (emailAddresses.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nessun indirizzo email caricato. Impossibile iniziare a inviare spam.", "Errore", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            currentSocket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            currentSocket.setSoTimeout(CONNECTION_TIMEOUT);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Errore nella connessione al server: " + e.getMessage(), "Errore", JOptionPane.ERROR_MESSAGE);
            return;
        }

        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::sendMultipleSpamToOneClient, 0, 10, TimeUnit.SECONDS);
    }

    private void stopSpamming() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (currentSocket != null && !currentSocket.isClosed()) {
            try {
                currentSocket.close();
                currentSocket = null;
            } catch (IOException e) {
                System.err.println("Errore nella chiusura del socket: " + e.getMessage());
            }
        }
    }

    private void sendMultipleSpamToOneClient() {
        if (!isRunning || currentSocket == null || currentSocket.isClosed()) return;

        String recipient = emailAddresses.get(random.nextInt(emailAddresses.size()));


        List<String> usedSenders = new ArrayList<>();
        int numberOfEmails = 2 + random.nextInt(3);

        for (int i = 0; i < numberOfEmails && isRunning; i++) {
            String sender;
            do {
                sender = SPAM_SENDERS.get(random.nextInt(SPAM_SENDERS.size()));
            } while (usedSenders.contains(sender));
            usedSenders.add(sender);

            sendSpamEmail(recipient, sender);

            try {
                Thread.sleep(DELAY_BETWEEN_EMAILS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void sendSpamEmail(String recipient, String sender) {
        if (currentSocket == null || currentSocket.isClosed()) {
            System.err.println("Socket non disponibile per l'invio");
            return;
        }

        Email spamEmail = createSpamEmail(recipient, sender);
        try {
            NetworkUtils.sendObject(currentSocket, "SEND_EMAIL");
            NetworkUtils.sendObject(currentSocket, spamEmail);
        } catch (Exception e) {
            reconnect();
        }
    }

    private void reconnect() {
        try {
            if (currentSocket != null && !currentSocket.isClosed()) {
                currentSocket.close();
            }
            currentSocket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            currentSocket.setSoTimeout(CONNECTION_TIMEOUT);
        } catch (IOException e) {
            System.err.println("Errore durante la riconnessione al server: " + e.getMessage());
        }
    }

    private Email createSpamEmail(String recipient, String sender) {
        Email email = new Email();
        email.setSender(sender);
        email.setRecipients(List.of(recipient));
        email.setSubject(SPAM_SUBJECTS.get(random.nextInt(SPAM_SUBJECTS.size())));
        email.setBody(SPAM_BODIES.get(random.nextInt(SPAM_BODIES.size())));
        return email;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SpamGenerator spamGenerator = new SpamGenerator();
            spamGenerator.setVisible(true);
        });
    }
}