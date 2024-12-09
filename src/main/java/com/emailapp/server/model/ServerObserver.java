package com.emailapp.server.model;

import com.emailapp.util.Email;

public interface ServerObserver {
    void onEmailSent(Email email);
    void onEmailReceived(Email email);
    void onEmailDeleted(int emailId);
    void onServerStateChanged(boolean isRunning);
}
