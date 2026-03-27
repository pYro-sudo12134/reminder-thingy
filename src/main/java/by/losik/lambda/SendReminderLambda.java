package by.losik.lambda;

import by.losik.composition.root.AWSModule;
import by.losik.composition.root.MailModule;
import by.losik.config.LambdaConfig;
import by.losik.service.VoiceReminderService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.fasterxml.jackson.databind.JsonNode;

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

    /** Singleton Injector для кэширования между вызовами Lambda */
    private static final Injector injector;

    static {
        injector = Guice.createInjector(new AWSModule(), new MailModule());
    }

    private final VoiceReminderService reminderService;
    private final LambdaConfig lambdaConfig;

    /**
     * Создаёт Lambda функцию с внедрёнными зависимостями.
     */
    public SendReminderLambda() {
        this.reminderService = injector.getInstance(VoiceReminderService.class);
        this.lambdaConfig = injector.getInstance(LambdaConfig.class);
    }

    /**
     * Обрабатывает событие EventBridge и отправляет напоминание.
     * <p>
     * Этапы обработки:
     * <ol>
     *     <li>Извлекает reminderId и userEmail из события</li>
     *     <li>Вызывает VoiceReminderService.sendReminder()</li>
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

            if (reminderId == null || reminderId.isEmpty() ||
                userEmail == null || userEmail.isEmpty()) {
                context.getLogger().log("Missing required fields in event detail: reminderId=" + reminderId + ", userEmail=" + userEmail);
                return "Missing required fields (reminderId, userEmail)";
            }

            if (lambdaConfig.isEnableDetailedLogging()) {
                context.getLogger().log("Processing reminder: " + reminderId + " for email: " + userEmail);
            }

            CompletableFuture<Void> future = reminderService.sendReminder(reminderId, userEmail);
            future.get(lambdaConfig.getSendReminderTimeoutSec(), TimeUnit.SECONDS);

            String result = "Reminder processed: " + reminderId;
            if (lambdaConfig.isEnableDetailedLogging()) {
                context.getLogger().log(result);
            }
            return result;

        } catch (java.util.concurrent.TimeoutException e) {
            String errorMsg = String.format("Timeout after %d sec while processing reminder", 
                lambdaConfig.getSendReminderTimeoutSec());
            context.getLogger().log(errorMsg);
            throw new RuntimeException(errorMsg, e);

        } catch (Exception e) {
            String errorMsg = String.format("Failed to process reminder: %s", e.getMessage());
            context.getLogger().log(errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }
}