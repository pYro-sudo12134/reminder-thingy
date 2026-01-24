package by.losik.service;

import by.losik.config.LocalStackConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ses.SesAsyncClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.concurrent.CompletableFuture;

@Singleton
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final SesAsyncClient sesAsyncClient;
    private final String fromEmail;

    @Inject
    public EmailService(LocalStackConfig config) {
        this.fromEmail = config.getEmail();
        this.sesAsyncClient = config.getSesAsyncClient();
    }

    public CompletableFuture<String> sendReminderEmail(String toEmail, String subject, String body) {
        Destination destination = Destination.builder()
                .toAddresses(toEmail)
                .build();

        Content emailSubject = Content.builder()
                .data(subject)
                .build();

        Content emailBody = Content.builder()
                .data(body)
                .build();

        Body emailBodyContent = Body.builder()
                .html(emailBody)
                .build();

        Message message = Message.builder()
                .subject(emailSubject)
                .body(emailBodyContent)
                .build();

        SendEmailRequest request = SendEmailRequest.builder()
                .source(fromEmail)
                .destination(destination)
                .message(message)
                .build();

        return sesAsyncClient.sendEmail(request)
                .thenApply(response -> {
                    log.info("Email sent successfully to {}: {}", toEmail, response.messageId());
                    return response.messageId();
                })
                .exceptionally(ex -> {
                    log.error("Failed to send email to {}", toEmail, ex);
                    throw new RuntimeException("Failed to send email", ex);
                });
    }

    public CompletableFuture<String> sendReminderNotification(
            String toEmail,
            String reminderId,
            String action,
            String scheduledTime
    ) {
        String subject = "Напоминание: " + action;

        String htmlBody = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }
                    .content { padding: 30px; background-color: #f9f9f9; }
                    .reminder { background-color: white; padding: 20px; border-left: 5px solid #4CAF50; margin: 20px 0; }
                    .footer { text-align: center; padding: 20px; color: #777; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Голосовое напоминание</h1>
                    </div>
                    <div class="content">
                        <div class="reminder">
                            <h2>%s</h2>
                            <p><strong>Время:</strong> %s</p>
                            <p><strong>ID напоминания:</strong> %s</p>
                        </div>
                        <p>Это напоминание было создано на основе вашего голосового сообщения.</p>
                    </div>
                </div>
            </body>
            </html>
            """, action, scheduledTime, reminderId);

        return sendReminderEmail(toEmail, subject, htmlBody);
    }
}