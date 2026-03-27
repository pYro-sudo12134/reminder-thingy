package by.losik.config;

import by.losik.util.ConfigUtils;
import com.google.inject.Singleton;

/**
 * Конфигурация шин AWS EventBridge.
 * <p>
 * Определяет имена шин событий для различных типов уведомлений:
 * <ul>
 *     <li>Email шина — для отправки email уведомлений (напоминания, сброс пароля)</li>
 *     <li>Telegram шина — для отправки Telegram уведомлений</li>
 * </ul>
 * <p>
 * Настройки загружаются из переменных окружения через ConfigUtils:
 * <ul>
 *     <li>{@code EVENTBRIDGE_EMAIL_BUS} — имя шины для email (по умолчанию "email-events")</li>
 *     <li>{@code EVENTBRIDGE_TELEGRAM_BUS} — имя шины для Telegram (по умолчанию "telegram-events")</li>
 * </ul>
 *
 * @see by.losik.service.EventBridgeService
 */
@Singleton
public class EventBridgeConfig {

    /** Имя шины для email уведомлений по умолчанию */
    private static final String DEFAULT_EMAIL_BUS = "email-events";

    /** Имя шины для Telegram уведомлений по умолчанию */
    private static final String DEFAULT_TELEGRAM_BUS = "telegram-events";

    private final String emailEventBusName;
    private final String telegramEventBusName;

    /**
     * Создаёт конфигурацию EventBridge с загрузкой настроек из переменных окружения.
     */
    public EventBridgeConfig() {
        this.emailEventBusName = ConfigUtils.getEnvOrDefault(
                "EVENTBRIDGE_EMAIL_BUS", DEFAULT_EMAIL_BUS);
        this.telegramEventBusName = ConfigUtils.getEnvOrDefault(
                "EVENTBRIDGE_TELEGRAM_BUS", DEFAULT_TELEGRAM_BUS);
    }

    /**
     * Получает имя шины для email уведомлений.
     *
     * @return имя шины email (например, "email-events")
     */
    public String getEmailEventBusName() {
        return emailEventBusName;
    }

    /**
     * Получает имя шины для Telegram уведомлений.
     *
     * @return имя шины Telegram (например, "telegram-events")
     */
    public String getTelegramEventBusName() {
        return telegramEventBusName;
    }
}
