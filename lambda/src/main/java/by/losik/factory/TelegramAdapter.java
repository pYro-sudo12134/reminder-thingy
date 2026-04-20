package by.losik.factory;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TelegramAdapter {
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot%s/sendMessage";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String botToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TelegramAdapter(String botToken) {
        this.botToken = botToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static TelegramAdapter create() {
        String botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        if (botToken == null || botToken.isEmpty()) {
            throw new RuntimeException("TELEGRAM_BOT_TOKEN not configured");
        }
        return new TelegramAdapter(botToken);
    }

    public CompletableFuture<TelegramSendResult> sendMessage(Long chatId, String text, String parseMode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = String.format(TELEGRAM_API_URL, botToken);

                String json = objectMapper.writeValueAsString(Map.of(
                        "chat_id", chatId,
                        "text", text,
                        "parse_mode", parseMode != null ? parseMode : "Markdown"
                ));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .timeout(TIMEOUT)
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                return parseResponse(response);

            } catch (Exception e) {
                throw new RuntimeException("Failed to send Telegram message: " + e.getMessage(), e);
            }
        });
    }

    private TelegramSendResult parseResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String body = response.body();

        try {
            Map<String, Object> result = objectMapper.readValue(body, Map.class);

            if (statusCode == 200 && result.containsKey("result")) {
                Map<String, Object> resultObj = (Map<String, Object>) result.get("result");
                Long messageId = ((Number) resultObj.get("message_id")).longValue();
                return new TelegramSendResult(true, messageId, null);
            } else {
                String errorDescription = result.containsKey("description")
                        ? (String) result.get("description")
                        : "Unknown error";
                return new TelegramSendResult(false, null, errorDescription);
            }
        } catch (Exception e) {
            return new TelegramSendResult(false, null, "Failed to parse response: " + e.getMessage());
        }
    }

    public record TelegramSendResult(boolean success, Long messageId, String errorDescription) {
    }
}