package by.losik.service;

import by.losik.config.TelegramConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class TelegramBotService {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    private final TelegramConfig config;

    private final Map<String, Long> userChatMapping = new ConcurrentHashMap<>();
    private final Map<String, String> pendingConnectCodes = new ConcurrentHashMap<>();

    @Inject
    public TelegramBotService(TelegramConfig config) {
        this.config = config;

        if (config.isEnabled()) {
            log.info("Telegram Bot Service initialized for bot: {}", config.getBotUsername().orElse("unknown"));
        } else {
            log.warn("Telegram Bot Service disabled - no bot token configured");
        }
    }

    public CompletableFuture<Boolean> sendReminderNotification(Long chatId, String reminderId,
                                                String action, String scheduledTime) {
        if (!config.isEnabled()) {
            log.warn("Telegram Bot not configured, skipping notification");
            return CompletableFuture.completedFuture(false);
        }

        if (chatId == null) {
            log.error("ChatId is null, cannot send reminder");
            return CompletableFuture.completedFuture(false);
        }

        String message = String.format(
                "⏰ *Напоминание*\n\n" +
                "📝 *Что:* %s\n" +
                "🕐 *Когда:* %s\n" +
                "🆔 *ID:* `%s`",
                escapeMarkdown(action),
                escapeMarkdown(scheduledTime),
                reminderId
        );

        return sendMessageAsync(chatId, message);
    }

    public CompletableFuture<Boolean> sendMessageAsync(Long chatId, String text) {
        if (!config.isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String token = config.getBotToken().orElseThrow();
                String url = "https://api.telegram.org/bot" + token + "/sendMessage";

                String json = String.format(
                        "{\"chat_id\":\"%d\",\"text\":\"%s\",\"parse_mode\":\"Markdown\"}",
                        chatId, text
                );

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(10))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    log.info("Message sent to chat {}", chatId);
                    return true;
                } else {
                    log.error("Failed to send message to chat {}: {}", chatId, response.body());
                    return false;
                }
            } catch (Exception e) {
                log.error("Failed to send message to chat {}: {}", chatId, e.getMessage());
                return false;
            }
        });
    }

    public String generateConnectCode(String userId) {
        String code = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        pendingConnectCodes.put(code, userId);
        log.info("Generated connect code {} for user {}", code, userId);
        return code;
    }

    public String activateConnectCode(String code, Long chatId) {
        String userId = pendingConnectCodes.remove(code);

        if (userId != null) {
            linkUserToChat(userId, chatId);
            log.info("User {} connected to Telegram chat {} via code {}", userId, chatId, code);
            return userId;
        }

        log.warn("Invalid or expired connect code: {}", code);
        return null;
    }

    public void linkUserToChat(String userId, Long chatId) {
        userChatMapping.put(userId, chatId);
        log.info("Linked user {} to chat {}", userId, chatId);
    }

    public Optional<Long> getChatIdForUser(String userId) {
        return Optional.ofNullable(userChatMapping.get(userId));
    }

    public void unlinkUser(String userId) {
        Long removed = userChatMapping.remove(userId);
        if (removed != null) {
            log.info("Unlinked user {} from chat {}", userId, removed);
        }
    }

    public boolean isUserLinked(String userId) {
        return userChatMapping.containsKey(userId);
    }

    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("`", "\\`");
    }

    public CompletableFuture<Boolean> sendHelpMessage(Long chatId) {
        String message = "🤖 *Voice Reminder Bot*\n\n" +
                "Я бот для отправки голосовых напоминаний.\n\n" +
                "*Команды:*\n" +
                "/start - Подключить бота\n" +
                "/help - Эта справка\n" +
                "/status - Проверить статус подключения";

        return sendMessageAsync(chatId, message);
    }

    public TelegramConfig getConfig() {
        return config;
    }
}