package com.emailapp.util;

import com.emailapp.client.model.Email;
import java.io.*;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class EmailFileManager {
    private static final String BASE_DIR = "emails";
    private static final String INBOX_DIR = "inbox";
    private static final String SENT_DIR = "sent";
    private static final String ID_FILE = "last_id.txt";
    private static final AtomicInteger idCounter = new AtomicInteger(0);

    static {
        initializeIdCounter();
    }

    private static void initializeIdCounter() {
        Path idFilePath = Paths.get(BASE_DIR, ID_FILE);
        try {
            Files.createDirectories(Paths.get(BASE_DIR));
            if (Files.exists(idFilePath)) {
                String lastId = Files.readString(idFilePath).trim();
                idCounter.set(Integer.parseInt(lastId));
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Errore nella lettura dell'ultimo ID: " + e.getMessage());
        }
    }

    private static void updateIdFile(int currentId) {
        Path idFilePath = Paths.get(BASE_DIR, ID_FILE);
        try {
            Files.writeString(idFilePath, String.valueOf(currentId));
        } catch (IOException e) {
            System.err.println("Errore nell'aggiornamento del file ID: " + e.getMessage());
        }
    }

    public static synchronized int getNextId() {
        int nextId = idCounter.incrementAndGet();
        updateIdFile(nextId);
        return nextId;
    }

    public static void saveEmail(Email email, String userEmail) throws IOException {
        // Se l'utente è il mittente, salva nella cartella sent
        if (email.getSender().equals(userEmail)) {
            saveEmailToDirectory(email, userEmail, SENT_DIR);
        }
        // Se l'utente è tra i destinatari, salva nella cartella inbox
        if (email.getRecipients().contains(userEmail)) {
            saveEmailToDirectory(email, userEmail, INBOX_DIR);
        }
    }

    private static void saveEmailToDirectory(Email email, String userEmail, String directory) throws IOException {
        // Crea il percorso completo: emails/userEmail/sent o emails/userEmail/inbox
        Path userDir = Paths.get(BASE_DIR, userEmail, directory);
        Files.createDirectories(userDir);

        String fileName = "Email_" + email.getId() + ".txt";
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

        // Carica email dalla inbox
        Path inboxDir = userDir.resolve(INBOX_DIR);
        if (Files.exists(inboxDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(inboxDir, "Email_*.txt")) {
                for (Path file : stream) {
                    Email email = readEmailFromFile(file);
                    if (email != null) {
                        emails.add(email);
                    }
                }
            }
        }

        // Carica email dalla cartella sent
        Path sentDir = userDir.resolve(SENT_DIR);
        if (Files.exists(sentDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(sentDir, "Email_*.txt")) {
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
        boolean deleted = false;

        // Prova a eliminare dalla cartella inbox
        Path inboxFile = Paths.get(BASE_DIR, userEmail, INBOX_DIR, "Email_" + emailId + ".txt");
        if (Files.exists(inboxFile)) {
            Files.delete(inboxFile);
            deleted = true;
        }

        // Prova a eliminare dalla cartella sent
        Path sentFile = Paths.get(BASE_DIR, userEmail, SENT_DIR, "Email_" + emailId + ".txt");
        if (Files.exists(sentFile)) {
            Files.delete(sentFile);
            deleted = true;
        }

        return deleted;
    }

    public static void markEmailAsRead(int emailId, String userEmail) throws IOException {
        // Cerca l'email nella cartella inbox
        Path inboxFile = Paths.get(BASE_DIR, userEmail, INBOX_DIR, "Email_" + emailId + ".txt");
        if (Files.exists(inboxFile)) {
            updateEmailReadStatus(inboxFile);
        }

        // Cerca l'email nella cartella sent
        Path sentFile = Paths.get(BASE_DIR, userEmail, SENT_DIR, "Email_" + emailId + ".txt");
        if (Files.exists(sentFile)) {
            updateEmailReadStatus(sentFile);
        }
    }

    private static void updateEmailReadStatus(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath);
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("Read:")) {
                lines.set(i, "Read: true");
                break;
            }
        }
        Files.write(filePath, lines);
    }

    public static List<String> loadValidEmails() throws IOException {
        List<String> emails = new ArrayList<>();
        try (InputStream inputStream = EmailFileManager.class.getResourceAsStream("/emails.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                emails.add(line.trim());
            }
        }
        return emails;
    }
}