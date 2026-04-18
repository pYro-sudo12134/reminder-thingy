package by.losik.service;

import java.util.concurrent.CompletableFuture;

public interface EmailSender {
    CompletableFuture<String> sendEmail(String to, String subject, String body, boolean isHtml);

    CompletableFuture<String> sendReminderNotification(String userEmail, String reminderId, String s, String s1);
}