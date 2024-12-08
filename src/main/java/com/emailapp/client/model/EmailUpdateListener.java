package com.emailapp.client.model;

import java.util.List;

public interface EmailUpdateListener {
    void onNewEmailsReceived(List<Email> newEmails);
    void onEmailUpdateError(Exception e);
}