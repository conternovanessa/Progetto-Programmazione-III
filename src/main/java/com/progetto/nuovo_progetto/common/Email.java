package com.progetto.nuovo_progetto.common;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public class Email implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String sender;
    private List<String> recipients;
    private String subject;
    private String body;
    private LocalDateTime sentDate;

    // Constructor, getters, and setters
    // ...
}