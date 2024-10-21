package com.emailapp;

import com.emailapp.client.Email;

import java.io.*;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class EmailFileManager {
    private static final String BASE_DIR = "emails";
    private static final String ID_FILE = "last_id.txt";
    private static final AtomicInteger idCounter = new AtomicInteger(0);

    static {
        initializeIdCounter();
    }

    private static void initializeIdCounter() {
        Path idFilePath = Paths.get(BASE_DIR, ID_FILE);
        if (Files.exists(idFilePath)) {
            try {
                String lastId = Files.readString(idFilePath).trim();
                idCounter.set(Integer.parseInt(lastId));
            } catch (IOException | NumberFormatException e) {
                System.err.println("Errore nella lettura dell'ultimo ID: " + e.getMessage());
            }
        }
    }

    private static void updateIdFile() {
        Path idFilePath = Paths.get(BASE_DIR, ID_FILE);
        try {
            Files.writeString(idFilePath, String.valueOf(idCounter.get()));
        } catch (IOException e) {
            System.err.println("Errore nell'aggiornamento del file ID: " + e.getMessage());
        }
    }

    public static synchronized int getNextId() {
        int nextId = idCounter.incrementAndGet();
        updateIdFile();
        return nextId;
    }

    public static void saveEmail(Email email, String userEmail) throws IOException {
        if (!email.getRecipients().contains(userEmail)) {
            return; // Non salvare l'email se l'utente corrente non è tra i destinatari
        }

        Path userDir = Paths.get(BASE_DIR, userEmail);
        Files.createDirectories(userDir);

        // Genera un nuovo ID univoco e sequenziale per l'email
        int newId = getNextId();
        email.setId(newId);

        // Genera un nome file con il nuovo formato ID
        String fileName = "Email_" + newId + ".txt";
        Path filePath = userDir.resolve(fileName);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("Id: " + email.getId());
            writer.newLine();
            writer.write("From: " + email.getSender());
            writer.newLine();
            writer.write("To: " + String.join(", ", email.getRecipients()));
            writer.newLine();
            writer.write("Subject: " + email.getSubject());
            writer.newLine();
            writer.write("Date: " + email.getSentDate());
            writer.newLine();
            writer.write("Read: false");
            writer.newLine();
            writer.write("Body: " + email.getBody());
        }
    }

    public static List<Email> loadEmails(String userEmail) throws IOException {
        List<Email> emails = new ArrayList<>();
        Path userDir = Paths.get(BASE_DIR, userEmail);

        if (Files.exists(userDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(userDir, "Email_*.txt")) {
                for (Path file : stream) {
                    Email email = readEmailFromFile(file);
                    if (email != null) {
                        emails.add(email);
                    }
                }
            }
        }
        return emails;
    }

    private static Email readEmailFromFile(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            int id = Integer.parseInt(reader.readLine().substring(4));
            String sender = reader.readLine().substring(6);
            List<String> recipients = List.of(reader.readLine().substring(4).split(", "));
            String subject = reader.readLine().substring(9);
            String dateStr = reader.readLine().substring(6);
            boolean read = Boolean.parseBoolean(reader.readLine().substring(6));
            String bodyLine = reader.readLine();
            String body = bodyLine.substring(6);

            Email email = new Email(sender, recipients, subject, body);
            email.setId(id);
            email.setSentDate(java.time.LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            email.setRead(read);
            return email;
        }
    }

    public static boolean deleteEmail(int emailId, String userEmail) throws IOException {
        Path userDir = Paths.get(BASE_DIR, userEmail);
        if (!Files.exists(userDir)) {
            return false; // The user directory doesn't exist, so there's nothing to delete
        }

        String fileName = "Email_" + emailId + ".txt";
        Path filePath = userDir.resolve(fileName);

        if (Files.exists(filePath)) {
            try {
                Files.delete(filePath);
                return true; // File successfully deleted
            } catch (IOException e) {
                System.err.println("Failed to delete file: " + filePath);
                e.printStackTrace();
                return false; // File deletion failed
            }
        } else {
            return false; // File doesn't exist
        }
    }

    public static void markEmailAsRead(int emailId, String userEmail) throws IOException {
        Path userDir = Paths.get(BASE_DIR, userEmail);
        Path filePath = userDir.resolve("Email_" + emailId + ".txt");

        if (Files.exists(filePath)) {
            List<String> lines = Files.readAllLines(filePath);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("Read:")) {
                    lines.set(i, "Read: true");
                    break;
                }
            }
            Files.write(filePath, lines);
        }
    }
}