package by.losik.service;

import by.losik.factory.TelegramAdapter;
import by.losik.factory.TelegramAdapter.TelegramSendResult;

import java.util.concurrent.CompletableFuture;

public class TelegramService {
    private final TelegramAdapter adapter;

    public TelegramService() {
        this.adapter = TelegramAdapter.create();
    }

    public CompletableFuture<TelegramSendResult> sendReminderNotification(
            Long chatId,
            String reminderId,
            String action,
            String scheduledTime) {

        if (chatId == null) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("ChatId is required"));
        }

        String message = formatMessage(reminderId, action, scheduledTime);

        return adapter.sendMessage(chatId, message, "Markdown");
    }

    private String formatMessage(String reminderId, String action, String scheduledTime) {
        return String.format(
                "⏰ *Напоминание*\n\n" +
                        "📝 *Что:* %s\n" +
                        "🕐 *Когда:* %s\n" +
                        "🆔 *ID:* `%s`",
                escapeMarkdown(action),
                escapeMarkdown(scheduledTime),
                reminderId
        );
    }

    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("`", "\\`");
    }
}