package com.emailapp.client;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Email implements Serializable {
    private String id;
    private String sender;
    private List<String> recipients;
    private String subject;
    private String body;
    private LocalDateTime sentDate;
    private boolean isRead;
    private List<String> attachments;

    public Email() {
        this.id = generateId();
        this.recipients = new ArrayList<>();
        this.sentDate = LocalDateTime.now();
        this.isRead = false;
        this.attachments = new ArrayList<>();
    }

    public Email(String sender, List<String> recipients, String subject, String body) {
        this();
        this.sender = sender;
        this.recipients = new ArrayList<>(recipients);
        this.subject = subject;
        this.body = body;
    }

    private String generateId() {
        return System.currentTimeMillis() + "-" + Math.random();
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getSentDate() {
        return sentDate;
    }
    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<String> getRecipients() {
        return new ArrayList<>(this.recipients); // Ritorna una copia della lista dei destinatari
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = new ArrayList<>(recipients);
    }


    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public List<String> getAttachments() {
        return new ArrayList<>(attachments);
    }

    public void addAttachment(String attachment) {
        this.attachments.add(attachment);
    }

    public void removeAttachment(String attachment) {
        this.attachments.remove(attachment);
    }

    public boolean hasAttachments() {
        return !this.attachments.isEmpty();
    }

    // Metodi aggiuntivi
    public void addRecipient(String recipient) {
        this.recipients.add(recipient);
    }

    public void removeRecipient(String recipient) {
        this.recipients.remove(recipient);
    }

    public boolean hasRecipient(String recipient) {
        return this.recipients.contains(recipient);
    }

    public int getRecipientCount() {
        return this.recipients.size();
    }

    public boolean isEmpty() {
        return (this.subject == null || this.subject.isEmpty()) &&
                (this.body == null || this.body.isEmpty()) &&
                this.recipients.isEmpty();
    }

    /*public Email createReply() {
        Email reply = new Email();
        reply.addRecipient(this.sender);
        reply.setSubject("Re: " + this.subject);
        reply.setBody("\n\nOn " + this.sentDate + ", " + this.sender + " wrote:\n" + this.body);
        return reply;
    }*/

    /*public Email createReplyAll() {
        Email replyAll = createReply();
        for (String recipient : this.recipients) {
            if (!recipient.equals(this.sender)) {
                replyAll.addRecipient(recipient);
            }
        }
        return replyAll;
    }*/

    /*public Email createForward() {
        Email forward = new Email();
        forward.setSubject("Fwd: " + this.subject);
        forward.setBody("\n\n---------- Forwarded message ---------\n" +
                "From: " + this.sender + "\n" +
                "Date: " + this.sentDate + "\n" +
                "Subject: " + this.subject + "\n" +
                "To: " + String.join(", ", this.recipients) + "\n\n" +
                this.body);
        forward.attachments.addAll(this.attachments);
        return forward;
    }*/

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return Objects.equals(id, email.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Email{" +
                "id='" + id + '\'' +
                ", sender='" + sender + '\'' +
                ", recipients=" + recipients +
                ", subject='" + subject + '\'' +
                ", sentDate=" + sentDate +
                ", isRead=" + isRead +
                ", hasAttachments=" + hasAttachments() +
                '}';
    }

    public void setSentDate(LocalDateTime parse) {
        this.sentDate = parse;
    }
}