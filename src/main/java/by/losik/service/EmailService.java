package by.losik.service;

import by.losik.config.EmailConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class EmailService implements EmailSender, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String fromEmail;
    private final boolean useSsl;
    private final boolean useTls;
    private final ExecutorService executorService;

    @Inject
    public EmailService(EmailConfig emailConfig) {
        this.smtpHost = emailConfig.getSmtpHost();
        this.smtpPort = emailConfig.getSmtpPort();
        this.smtpUsername = emailConfig.getSmtpUsername();
        this.smtpPassword = emailConfig.getSmtpPassword();
        this.fromEmail = emailConfig.getFromEmail();
        this.useSsl = emailConfig.isUseSsl();
        this.useTls = emailConfig.isUseTls();
        this.executorService = Executors.newFixedThreadPool(5);
    }

    protected Session createMailSession() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);

        if (useSsl) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", String.valueOf(smtpPort));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else if (useTls) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });
    }

    @Override
    public CompletableFuture<String> sendEmail(String toEmail, String subject, String body, boolean isHtml) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Session session = createMailSession();
                MimeMessage message = new MimeMessage(session);

                message.setFrom(new InternetAddress(fromEmail));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
                message.setSubject(subject, "UTF-8");

                if (isHtml) {
                    MimeBodyPart mimeBodyPart = new MimeBodyPart();
                    mimeBodyPart.setContent(body, "text/html; charset=utf-8");

                    Multipart multipart = new MimeMultipart();
                    multipart.addBodyPart(mimeBodyPart);
                    message.setContent(multipart);
                } else {
                    message.setText(body, "UTF-8");
                }

                message.setSentDate(new java.util.Date());

                Transport.send(message);

                String messageId = message.getMessageID();
                log.info("Email sent successfully to {}: {}", toEmail, messageId);
                return messageId;

            } catch (Exception e) {
                log.error("Failed to send email to {}", toEmail, e);
                throw new RuntimeException("Email sending failed", e);
            }
        }, executorService);
    }

    public CompletableFuture<String> sendReminderNotification(
            String toEmail,
            String reminderId,
            String action,
            String scheduledTime) {

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
                    <div class="footer">
                        <p>Это письмо отправлено автоматически, пожалуйста, не отвечайте на него.</p>
                    </div>
                </div>
            </body>
            </html>
            """, action, scheduledTime, reminderId);

        log.info("Sending mail: {}", htmlBody);
        return sendEmail(toEmail, subject, htmlBody, true);
    }

    @Override
    public void close() {
        executorService.shutdown();
    }
}