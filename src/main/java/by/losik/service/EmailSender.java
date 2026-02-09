package by.losik.service;

import java.util.concurrent.CompletableFuture;

public interface EmailSender {
    CompletableFuture<String> sendEmail(String to, String subject, String body, boolean isHtml);
}