package com.emailapp.util;

import java.io.*;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class EmailFileManager {
    private static final String BASE_DIR = "emails";
    private static final String INBOX_DIR = "inbox";
    private static final String SENT_DIR = "sent";
    private static final String ID_FILE = "last_id.txt";
    private static final AtomicInteger idCounter = new AtomicInteger(0);
    private static final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private static final Object FILE_LOCK = new Object();

    static {
        initializeIdCounter();
    }

    private static void initializeIdCounter() {
        rwLock.writeLock().lock();
        try {
            Path idFilePath = Paths.get(BASE_DIR, ID_FILE);
            Files.createDirectories(Paths.get(BASE_DIR));
            if (Files.exists(idFilePath)) {
                String lastId = Files.readString(idFilePath).trim();
                idCounter.set(Integer.parseInt(lastId));
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Errore nella lettura dell'ultimo ID: " + e.getMessage());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private static void updateIdFile(int currentId) {
        rwLock.writeLock().lock();
        try {
            Path idFilePath = Paths.get(BASE_DIR, ID_FILE);
            Files.writeString(idFilePath, String.valueOf(currentId));
        } catch (IOException e) {
            System.err.println("Errore nell'aggiornamento del file ID: " + e.getMessage());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public static synchronized int getNextId() {
        int nextId = idCounter.incrementAndGet();
        updateIdFile(nextId);
        return nextId;
    }

    public static void saveEmail(Email email, String userEmail) throws IOException {
        synchronized(FILE_LOCK) {
            if (email.getSender().equals(userEmail)) {
                saveEmailToDirectory(email, userEmail, SENT_DIR);
            }
            if (email.getRecipients().contains(userEmail)) {
                saveEmailToDirectory(email, userEmail, INBOX_DIR);
            }
        }
    }

    private static void saveEmailToDirectory(Email email, String userEmail, String directory) throws IOException {
        synchronized(FILE_LOCK) {
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
    }

    public static List<Email> loadEmails(String userEmail) throws IOException {
        rwLock.readLock().lock();
        try {
            List<Email> emails = new ArrayList<>();
            Path userDir = Paths.get(BASE_DIR, userEmail);

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
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private static Email readEmailFromFile(Path file) throws IOException {
        synchronized(FILE_LOCK) {
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                int id = Integer.parseInt(reader.readLine().substring(4));
                String sender = reader.readLine().substring(6);
                List<String> recipients = List.of(reader.readLine().substring(4).split(", "));
                String subject = reader.readLine().substring(9);
                String dateStr = reader.readLine().substring(6);
                boolean read = Boolean.parseBoolean(reader.readLine().substring(6));

                // Legge la prima riga del body (che inizia con "Body: ")
                String firstBodyLine = reader.readLine().substring(6);

                // Legge il resto del corpo dell'email
                StringBuilder bodyBuilder = new StringBuilder(firstBodyLine);
                String line;
                while ((line = reader.readLine()) != null) {
                    bodyBuilder.append("\n").append(line);
                }

                Email email = new Email(sender, recipients, subject, bodyBuilder.toString());
                email.setId(id);
                email.setSentDate(java.time.LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                email.setRead(read);
                return email;
            }
        }
    }


    public static boolean deleteEmail(int emailId, String userEmail) throws IOException {
        synchronized(FILE_LOCK) {
            boolean deleted = false;

            Path inboxFile = Paths.get(BASE_DIR, userEmail, INBOX_DIR, "Email_" + emailId + ".txt");
            if (Files.exists(inboxFile)) {
                Files.delete(inboxFile);
                deleted = true;
            }

            Path sentFile = Paths.get(BASE_DIR, userEmail, SENT_DIR, "Email_" + emailId + ".txt");
            if (Files.exists(sentFile)) {
                Files.delete(sentFile);
                deleted = true;
            }

            return deleted;
        }
    }

    public static List<String> loadValidEmails() throws IOException {
        rwLock.readLock().lock();
        try {
            List<String> emails = new ArrayList<>();
            try (InputStream inputStream = EmailFileManager.class.getResourceAsStream("/emails.txt");
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    emails.add(line.trim());
                }
            }
            return emails;
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
