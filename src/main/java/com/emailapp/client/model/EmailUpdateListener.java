package com.emailapp.client.model;

import com.emailapp.util.Email;

import java.util.List;

public interface EmailUpdateListener {
    void onNewEmailsReceived(List<Email> newEmails);
    void onEmailsFiltered(String filter, List<Email> emails);
    void onEmailMarkedAsRead(Email email);
    void onEmailUpdateError(Exception e);
}