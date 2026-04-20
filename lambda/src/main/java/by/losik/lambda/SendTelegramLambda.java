package by.losik.lambda;

import by.losik.service.TelegramService;
import by.losik.factory.TelegramAdapter.TelegramSendResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Lambda функция для отправки Telegram-напоминаний по расписанию EventBridge.
 * <p>
 * Получает событие с данными напоминания (reminderId, chatId, action, scheduledTime)
 * и отправляет сообщение через Telegram Bot API.
 * <p>
 * Ожидаемая структура события:
 * <pre>
 * {
 *   "detail": {
 *     "reminderId": "reminder-123",
 *     "chatId": 123456789,
 *     "action": "Встреча с клиентом",
 *     "scheduledTime": "2024-03-28 15:00:00"
 *   }
 * }
 * </pre>
 */
public class SendTelegramLambda implements RequestHandler<ScheduledEvent, String> {

    private static final long DEFAULT_TIMEOUT_SEC = 30L;

    private final TelegramService telegramService;

    public SendTelegramLambda() {
        try {
            this.telegramService = new TelegramService();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TelegramService", e);
        }
    }

    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        try {
            Map<String, Object> detailMap = event.getDetail();

            if (detailMap == null || detailMap.isEmpty()) {
                context.getLogger().log("Event detail is empty");
                return "No detail in event";
            }

            String reminderId = (String) detailMap.get("reminderId");
            Object chatIdObj = detailMap.get("chatId");
            String action = (String) detailMap.get("action");
            String scheduledTime = (String) detailMap.get("scheduledTime");

            if (reminderId == null || reminderId.isEmpty()) {
                context.getLogger().log("Missing required field: reminderId");
                return "Missing required field: reminderId";
            }

            if (chatIdObj == null) {
                context.getLogger().log("Missing required field: chatId");
                return "Missing required field: chatId";
            }

            Long chatId;
            if (chatIdObj instanceof Number) {
                chatId = ((Number) chatIdObj).longValue();
            } else {
                chatId = Long.parseLong(chatIdObj.toString());
            }

            context.getLogger().log("Processing reminder: " + reminderId + " for chat: " + chatId);

            CompletableFuture<TelegramSendResult> future = telegramService.sendReminderNotification(
                    chatId,
                    reminderId,
                    action != null ? action : "Напоминание",
                    scheduledTime != null ? scheduledTime : "Неизвестно");

            TelegramSendResult result = future.get(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS);

            if (result.success()) {
                String response = "Reminder processed: " + reminderId +
                        ", messageId: " + result.messageId();
                context.getLogger().log(response);
                return response;
            } else {
                throw new RuntimeException("Failed to send: " + result.errorDescription());
            }

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