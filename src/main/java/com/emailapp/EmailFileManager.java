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
        for (String recipient : email.getRecipients()) { // Per ogni destinatario
            Path userDir = Paths.get(BASE_DIR, recipient); // Crea la directory per il destinatario
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
                writer.write("Body: " + email.getBody());
            }
        }
    }


    public static List<Email> loadEmails(String userEmail) throws IOException {
        List<Email> emails = new ArrayList<>();
        Path userDir = Paths.get(BASE_DIR, userEmail); // Directory del destinatario

        if (Files.exists(userDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(userDir, "*.txt")) {
                for (Path file : stream) {
                    emails.add(readEmailFromFile(file));
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
            reader.readLine(); // Empty line
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line).append("\n");
            }

            Email email = new Email(sender, recipients, subject, body.toString());
            email.setSentDate(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(dateStr, LocalDateTime::from));
            return email;
        }
    }

    public static void deleteEmail(String emailId, String userEmail) throws IOException {
        Path userDir = Paths.get(BASE_DIR, userEmail);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(userDir, "*" + emailId + ".txt")) {
            for (Path file : stream) {
                Files.delete(file);
                break; // Should only be one file matching the ID
            }
        }
    }
}

