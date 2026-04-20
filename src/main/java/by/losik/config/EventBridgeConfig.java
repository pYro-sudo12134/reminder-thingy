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
 *     <li>ARN Lambda функции для обработки событий</li>
 * </ul>
 * <p>
 * Настройки загружаются из переменных окружения через ConfigUtils:
 * <ul>
 *     <li>{@code EVENTBRIDGE_EMAIL_BUS} — имя шины для email (по умолчанию "email-events")</li>
 *     <li>{@code EVENTBRIDGE_TELEGRAM_BUS} — имя шины для Telegram (по умолчанию "telegram-events")</li>
 *     <li>{@code LAMBDA_SEND_REMINDER_ARN} — ARN Lambda функции для напоминаний</li>
 * </ul>
 *
 * @see by.losik.service.EventBridgeService
 */
@Singleton
public class EventBridgeConfig {

    /** Имя шины для email уведомлений по умолчанию */
    private static final String DEFAULT_EMAIL_BUS = "default";

    /** Имя шины для Telegram уведомлений по умолчанию */
    private static final String DEFAULT_TELEGRAM_BUS = "default";

    /** ARN Lambda функции для напоминаний по умолчанию */
    private static final String DEFAULT_SEND_REMINDER_ARN = "arn:aws:lambda:us-east-1:000000000000:function:send-reminder-email";

    private final String emailEventBusName;
    private final String telegramEventBusName;
    private final String defaultLambdaArn;

    /**
     * Создаёт конфигурацию EventBridge с загрузкой настроек из переменных окружения.
     */
    public EventBridgeConfig() {
        this.emailEventBusName = ConfigUtils.getEnvOrDefault(
                "EVENTBRIDGE_EMAIL_BUS", DEFAULT_EMAIL_BUS);
        this.telegramEventBusName = ConfigUtils.getEnvOrDefault(
                "EVENTBRIDGE_TELEGRAM_BUS", DEFAULT_TELEGRAM_BUS);
        this.defaultLambdaArn = ConfigUtils.getEnvOrDefault(
                "LAMBDA_SEND_REMINDER_ARN", DEFAULT_SEND_REMINDER_ARN);
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

    /**
     * Получает ARN Lambda функции для напоминаний по умолчанию.
     *
     * @return ARN Lambda функции (по умолчанию "arn:aws:lambda:us-east-1:000000000000:function:send-reminder")
     */
    public String getDefaultLambdaArn() {
        return defaultLambdaArn;
    }
}
