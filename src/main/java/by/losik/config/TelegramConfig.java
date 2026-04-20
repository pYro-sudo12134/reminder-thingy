package by.losik.config;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Singleton
public class TelegramConfig {
    private static final Logger log = LoggerFactory.getLogger(TelegramConfig.class);

    private final String botToken;
    private final String botUsername;

    public TelegramConfig() {
        this.botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        this.botUsername = System.getenv("TELEGRAM_BOT_USERNAME");

        log.info("Telegram Bot configured: username={}, token configured={}",
                botUsername, botToken != null && !botToken.isEmpty());
    }

    public Optional<String> getBotToken() {
        return Optional.ofNullable(botToken).filter(t -> !t.isEmpty());
    }

    public Optional<String> getBotUsername() {
        return Optional.ofNullable(botUsername).filter(u -> !u.isEmpty());
    }

    public boolean isEnabled() {
        return getBotToken().isPresent();
    }
}