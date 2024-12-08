package com.emailapp.client.model;

import java.util.List;

public interface EmailUpdateListener {
    void onNewEmailsReceived(List<Email> newEmails);
    void onEmailsFiltered(String filter, List<Email> emails);
    void onEmailUpdateError(Exception e);

}