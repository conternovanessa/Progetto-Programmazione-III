package com.emailapp.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.emailapp.client.model.Email;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class EmailPersistence {
    private static final String EMAIL_DIRECTORY = "emails";
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public static void saveEmail(Email email) throws IOException {
        String fileName = email.getId() + ".json";
        Path filePath = Paths.get(EMAIL_DIRECTORY, fileName);
        Files.createDirectories(filePath.getParent());

        String json = gson.toJson(email);
        Files.write(filePath, json.getBytes());
    }

    public static Email loadEmail(String id) throws IOException {
        String fileName = id + ".json";
        Path filePath = Paths.get(EMAIL_DIRECTORY, fileName);

        String json = new String(Files.readAllBytes(filePath));
        return gson.fromJson(json, Email.class);
    }

    public static List<Email> loadAllEmails() throws IOException {
        List<Email> emails = new ArrayList<>();
        Path dirPath = Paths.get(EMAIL_DIRECTORY);

        if (Files.exists(dirPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "*.json")) {
                for (Path path : stream) {
                    String json = new String(Files.readAllBytes(path));
                    emails.add(gson.fromJson(json, Email.class));
                }
            }
        }

        return emails;
    }
}