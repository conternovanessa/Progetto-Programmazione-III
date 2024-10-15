package com.emailapp.client;

import com.emailapp.NetworkUtils;
import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    private ScheduledExecutorService scheduler;
    private final Random random = new Random();
    private final JButton toggleButton;
    private List<String> emailAddresses;

    public SpamGenerator() {
        super("Spam Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(300, 100);

        toggleButton = new JButton("Start Spamming");
        toggleButton.addActionListener(e -> toggleSpamming());

        setLayout(new FlowLayout());
        add(toggleButton);

        loadEmailAddresses();
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
            JOptionPane.showMessageDialog(this, "Error loading email addresses: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleSpamming() {
        if (scheduler == null || scheduler.isShutdown()) {
            startSpamming();
            toggleButton.setText("Stop Spamming");
        } else {
            stopSpamming();
            toggleButton.setText("Start Spamming");
        }
    }

    private void startSpamming() {
        if (emailAddresses.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No email addresses loaded. Cannot start spamming.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::sendSpamToAllClients, 0, 5, TimeUnit.SECONDS);
    }

    private void stopSpamming() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void sendSpamToAllClients() {
        for (String recipient : emailAddresses) {
            sendSpamEmail(recipient);
        }
    }

    private void sendSpamEmail(String recipient) {
        Email spamEmail = createSpamEmail(recipient);
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            NetworkUtils.sendObject(socket, "SEND_EMAIL");
            NetworkUtils.sendObject(socket, spamEmail);
            String response = (String) NetworkUtils.receiveObject(socket);
            if ("SUCCESS".equals(response)) {
                System.out.println("Spam email sent to " + recipient);
            } else {
                System.out.println("Failed to send spam email to " + recipient);
            }
        } catch (Exception e) {
            System.err.println("Error sending spam email to " + recipient + ": " + e.getMessage());
        }
    }

    private Email createSpamEmail(String recipient) {
        Email email = new Email();
        email.setSender("spammer@spam.com");
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