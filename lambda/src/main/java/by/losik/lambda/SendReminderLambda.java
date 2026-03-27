package by.losik.lambda;

import by.losik.config.EmailConfig;
import by.losik.service.EmailSender;
import by.losik.service.EmailService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Lambda функция для отправки напоминаний по расписанию EventBridge.
 * <p>
 * Вызывается EventBridge по расписанию для отправки email уведомлений пользователям.
 * Обработчик получает событие с деталями напоминания (reminderId, userEmail) и отправляет email.
 * <p>
 * Пример события EventBridge:
 * <pre>{@code
 * {
 *   "version": "0",
 *   "id": "event-id",
 *   "detail-type": "Reminder Notification",
 *   "source": "voice-reminder",
 *   "account": "000000000000",
 *   "time": "2024-01-01T10:00:00Z",
 *   "region": "us-east-1",
 *   "resources": [],
 *   "detail": {
 *     "reminderId": "reminder-123",
 *     "userEmail": "user@example.com"
 *   }
 * }
 * }</pre>
 *
 * @see ScheduledEvent
 */
public class SendReminderLambda implements RequestHandler<ScheduledEvent, String> {
    private static final long DEFAULT_TIMEOUT_SEC = 30L;

    private final EmailSender emailService;

    /**
     * Создаёт Lambda функцию с EmailService.
     */
    public SendReminderLambda() {
        this.emailService = createEmailService();
    }

    /**
     * Создаёт EmailService из переменных окружения.
     *
     * @return EmailService
     */
    private EmailSender createEmailService() {
        String smtpHost = System.getenv("SMTP_HOST");
        String smtpPortStr = System.getenv("SMTP_PORT");
        String smtpUsername = System.getenv("SMTP_USERNAME");
        String smtpPassword = System.getenv("SMTP_PASSWORD");
        String fromEmail = System.getenv("FROM_EMAIL");
        String useSslStr = System.getenv("SMTP_SSL");
        String useTlsStr = System.getenv("SMTP_TLS");

        int smtpPort = smtpPortStr != null ? Integer.parseInt(smtpPortStr) : 587;
        boolean useSsl = useSslStr != null && Boolean.parseBoolean(useSslStr);
        boolean useTls = useTlsStr == null || Boolean.parseBoolean(useTlsStr);

        if (smtpHost == null || smtpHost.isEmpty()) {
            smtpHost = "smtp.gmail.com";
        }
        if (smtpUsername == null || smtpUsername.isEmpty()) {
            smtpUsername = "losik2006@gmail.com";
        }
        if (smtpPassword == null || smtpPassword.isEmpty()) {
            smtpPassword = "your-app-password";
        }
        if (fromEmail == null || fromEmail.isEmpty()) {
            fromEmail = smtpUsername;
        }

        EmailConfig config = new EmailConfig(smtpHost, smtpPort, smtpUsername,
                smtpPassword, fromEmail, useSsl, useTls);

        return new EmailService(config);
    }

    /**
     * Обрабатывает событие EventBridge и отправляет напоминание.
     * <p>
     * Этапы обработки:
     * <ol>
     *     <li>Извлекает reminderId и userEmail из события</li>
     *     <li>Вызывает EmailService.sendReminderNotification()</li>
     *     <li>Возвращает результат выполнения</li>
     * </ol>
     *
     * @param event событие EventBridge с деталями напоминания
     * @param context контекст Lambda (логгер, AWS X-Ray, etc.)
     * @return результат выполнения ("Reminder processed: {reminderId}" или сообщение об ошибке)
     * @throws RuntimeException если не удалось отправить напоминание
     */
    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        try {
            Map<String, Object> detailMap = event.getDetail();

            if (detailMap == null || detailMap.isEmpty()) {
                context.getLogger().log("Event detail is empty");
                return "No detail in event";
            }

            String reminderId = (String) detailMap.get("reminderId");
            String userEmail = (String) detailMap.get("userEmail");
            String action = (String) detailMap.get("action");
            String scheduledTime = (String) detailMap.get("scheduledTime");

            if (reminderId == null || reminderId.isEmpty() ||
                userEmail == null || userEmail.isEmpty()) {
                context.getLogger().log("Missing required fields in event detail: reminderId=" + reminderId + ", userEmail=" + userEmail);
                return "Missing required fields (reminderId, userEmail)";
            }

            context.getLogger().log("Processing reminder: " + reminderId + " for email: " + userEmail);

            CompletableFuture<String> future = emailService.sendReminderNotification(
                    userEmail, reminderId, action != null ? action : "Напоминание",
                    scheduledTime != null ? scheduledTime : "Неизвестно");

            future.get(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS);

            String result = "Reminder processed: " + reminderId;
            context.getLogger().log(result);
            return result;

        } catch (java.util.concurrent.TimeoutException e) {
            String errorMsg = String.format("Timeout after %d sec while processing reminder", DEFAULT_TIMEOUT_SEC);
            context.getLogger().log(errorMsg);
            throw new RuntimeException(errorMsg, e);

        } catch (Exception e) {
            String errorMsg = String.format("Failed to process reminder: %s", e.getMessage());
            context.getLogger().log(errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }
}
