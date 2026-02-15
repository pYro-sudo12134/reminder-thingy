package by.losik.service;

import by.losik.dto.DLQMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class EmailSenderService {
    private static final Logger log = LoggerFactory.getLogger(EmailSenderService.class);
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .withZone(ZoneId.systemDefault());

    private final Session mailSession;
    private final ObjectMapper objectMapper;
    private final String fromEmail;
    private final ExecutorService emailExecutor;

    @Inject
    public EmailSenderService(
            Session mailSession,
            ObjectMapper objectMapper,
            @Named("from.email") String fromEmail) {
        this.mailSession = mailSession;
        this.objectMapper = objectMapper;
        this.fromEmail = fromEmail;
        this.emailExecutor = Executors.newCachedThreadPool();
    }

    public CompletableFuture<String> sendDLQAlert(DLQMessage message, String toEmail) {
        return CompletableFuture.supplyAsync(() -> {
            String subject = String.format("[DLQ ALERT] %s - %s (attempt: %d)",
                    message.getSource(), message.getDetailType(), message.getReceiveCount());

            String htmlBody = buildEmailBody(message);

            try {
                MimeMessage mimeMessage = new MimeMessage(mailSession);
                mimeMessage.setFrom(new InternetAddress(fromEmail));
                mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
                mimeMessage.setSubject(subject, "UTF-8");

                Multipart multipart = new MimeMultipart();

                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
                multipart.addBodyPart(htmlPart);

                mimeMessage.setContent(multipart);
                mimeMessage.setSentDate(new java.util.Date());

                Transport.send(mimeMessage);

                String messageId = mimeMessage.getMessageID();
                log.info("Email sent successfully for message {}: {}", message.getMessageId(), messageId);
                return messageId;

            } catch (MessagingException e) {
                log.error("Failed to send email for message {}: {}",
                        message.getMessageId(), e.getMessage(), e);
                throw new RuntimeException("Failed to send email", e);
            }
        }, emailExecutor);
    }

    private String buildEmailBody(DLQMessage message) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><style>")
                .append("body{font-family:Arial,sans-serif;margin:20px;}")
                .append("h1{color:#d9534f;}")
                .append("h2{color:#5bc0de;margin-top:20px;}")
                .append("table{border-collapse:collapse;width:100%;}")
                .append("th{background:#f2f2f2;text-align:left;padding:8px;}")
                .append("td{padding:8px;border-bottom:1px solid #ddd;}")
                .append(".warning{background:#fcf8e3;padding:10px;}")
                .append(".json{background:#f8f8f8;padding:10px;border:1px solid #ddd;font-family:monospace;}")
                .append("</style></head><body>")
                .append("<h1>Dead Letter Queue Alert</h1>");

        html.append("<h2>Message Details</h2><table>")
                .append("<tr><th>Message ID</th><td>").append(escapeHtml(message.getMessageId())).append("</td></tr>")
                .append("<tr><th>Source</th><td>").append(escapeHtml(message.getSource())).append("</td></tr>")
                .append("<tr><th>Type</th><td>").append(escapeHtml(message.getDetailType())).append("</td></tr>")
                .append("<tr><th>Timestamp</th><td>").append(DATE_FORMATTER.format(message.getTimestamp())).append("</td></tr>")
                .append("<tr><th>Receive Count</th><td>").append(message.getReceiveCount()).append("</td></tr>")
                .append("<tr><th>Queue</th><td>").append(escapeHtml(message.getQueueName())).append("</td></tr>")
                .append("</table>");

        if (message.getErrorMessage() != null && !message.getErrorMessage().isEmpty()) {
            html.append("<h2>Error</h2><div class='warning'>")
                    .append(escapeHtml(message.getErrorMessage()))
                    .append("</div>");
        }

        html.append("<h2>Full Detail</h2><div class='json'>");
        try {
            String json = message.isParsed()
                    ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(message.getDetail())
                    : message.getRawBody();
            html.append(escapeHtml(json));
        } catch (Exception e) {
            html.append("Error serializing: ").append(escapeHtml(e.getMessage()));
        }
        html.append("</div></body></html>");

        return html.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public void shutdown() {
        emailExecutor.shutdown();
    }
}