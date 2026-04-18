package by.losik.lambda;

import by.losik.factory.EmailServiceFactory;
import by.losik.service.EmailSender;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Lambda функция для отправки email-напоминаний по расписанию EventBridge.
 * <p>
 * Получает событие с данными напоминания (reminderId, userEmail, action, scheduledTime)
 * и отправляет email через SMTP сервер. Конфигурация загружается автоматически из
 * AWS Secrets Manager, Parameter Store или переменных окружения.
 * <p>
 * Ожидаемая структура события:
 * <pre>
 * {
 *   "detail": {
 *     "reminderId": "reminder-123",
 *     "userEmail": "user@example.com",
 *     "action": "Встреча с клиентом",
 *     "scheduledTime": "2024-03-28 15:00:00"
 *   }
 * }
 * </pre>
 */
public class SendReminderLambda implements RequestHandler<ScheduledEvent, String> {

    /** Таймаут ожидания отправки email в секундах */
    private static final long DEFAULT_TIMEOUT_SEC = 30L;

    /** Сервис для отправки email */
    private final EmailSender emailService;

    /**
     * Конструктор. Инициализирует сервис отправки email с конфигурацией
     * из доступных источников (Secrets Manager, Parameter Store, окружение).
     *
     * @throws RuntimeException если не удалось инициализировать сервис
     */
    public SendReminderLambda() {
        try {
            this.emailService = EmailServiceFactory.create();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize EmailService", e);
        }
    }

    /**
     * Основной метод обработки события от EventBridge.
     *
     * @param event событие с данными напоминания
     * @param context контекст выполнения Lambda
     * @return сообщение о результате обработки
     * @throws RuntimeException при ошибках отправки email или таймауте
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
                context.getLogger().log("Missing required fields: reminderId=" + reminderId +
                        ", userEmail=" + userEmail);
                return "Missing required fields (reminderId, userEmail)";
            }

            context.getLogger().log("Processing reminder: " + reminderId + " for email: " + userEmail);

            CompletableFuture<String> future = emailService.sendReminderNotification(
                    userEmail, reminderId,
                    action != null ? action : "Напоминание",
                    scheduledTime != null ? scheduledTime : "Неизвестно");

            String messageId = future.get(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS);

            String result = "Reminder processed: " + reminderId + ", messageId: " + messageId;
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