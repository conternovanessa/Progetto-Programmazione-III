package com.emailapp;

import com.emailapp.client.Email;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class EmailFileManager {
    private static final String BASE_DIR = "emails";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public static void saveEmail(Email email, String userEmail) throws IOException {
        // Save a copy for each recipient
        for (String recipient : email.getRecipients()) {
            saveEmailForUser(email, recipient);
        }
    }

    private static void saveEmailForUser(Email email, String userEmail) throws IOException {
        Path userDir = Paths.get(BASE_DIR, userEmail);
        Files.createDirectories(userDir);

        String fileName = email.getSentDate().format(DATE_FORMATTER) + "_" + email.getId() + ".txt";
        Path filePath = userDir.resolve(fileName);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("From: " + email.getSender());
            writer.newLine();
            writer.write("To: " + String.join(", ", email.getRecipients()));
            writer.newLine();
            writer.write("Subject: " + email.getSubject());
            writer.newLine();
            writer.write("Date: " + email.getSentDate());
            writer.newLine();
            writer.write("Deleted: false");
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
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(userDir, "*.txt")) {
                for (Path file : stream) {
                    Email email = readEmailFromFile(file);
                    if (email != null && !email.isDeleted()) {
                        emails.add(email);
                    }
                }
            }
        }
        return emails;
    }

    private static Email readEmailFromFile(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String sender = reader.readLine().substring(6);
            List<String> recipients = List.of(reader.readLine().substring(4).split(", "));
            String subject = reader.readLine().substring(9);
            String dateStr = reader.readLine().substring(6);
            boolean deleted = Boolean.parseBoolean(reader.readLine().substring(9));
            boolean read = Boolean.parseBoolean(reader.readLine().substring(6));
            String bodyLine = reader.readLine();
            String body = bodyLine.substring(6); // Assume that "Body: " is always present

            Email email = new Email(sender, recipients, subject, body);
            email.setSentDate(LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            email.setDeleted(deleted);
            email.setRead(read);
            email.setId(Long.parseLong(file.getFileName().toString().split("_")[1].replace(".txt", "")));
            return email;
        }
    }

    public static void deleteEmail(String emailId, String userEmail) throws IOException {
        Path userDir = Paths.get(BASE_DIR, userEmail);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(userDir, "*" + emailId + ".txt")) {
            for (Path file : stream) {
                markEmailAsDeleted(file);
                break; // Should only be one file matching the ID
            }
        }
    }

    private static void markEmailAsDeleted(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("Deleted:")) {
                lines.set(i, "Deleted: true");
                break;
            }
        }
        Files.write(file, lines);
    }

    public static void markEmailAsRead(String emailId, String userEmail) throws IOException {
        Path userDir = Paths.get(BASE_DIR, userEmail);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(userDir, "*" + emailId + ".txt")) {
            for (Path file : stream) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).startsWith("Read:")) {
                        lines.set(i, "Read: true");
                        break;
                    }
                }
                Files.write(file, lines);
                break; // Should only be one file matching the ID
            }
        }
    }

    public static void purgeDeletedEmails(String userEmail) throws IOException {
        Path userDir = Paths.get(BASE_DIR, userEmail);
        if (Files.exists(userDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(userDir, "*.txt")) {
                for (Path file : stream) {
                    if (isEmailDeleted(file)) {
                        Files.delete(file);
                    }
                }
            }
        }
    }

    private static boolean isEmailDeleted(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Deleted:")) {
                    return Boolean.parseBoolean(line.substring(9));
                }
            }
        }
        return false;
    }
}