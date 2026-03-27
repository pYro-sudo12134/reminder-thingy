package by.losik.composition.root;

import by.losik.config.EmailConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

/**
 * Guice модуль для конфигурации email сервиса (SMTP).
 * <p>
 * Предоставляет следующие зависимости:
 * <ul>
 *     <li>{@link EmailConfig} — конфигурация SMTP сервера</li>
 * </ul>
 * <p>
 * Конфигурация загружается из переменных окружения:
 * <ul>
 *     <li>{@code SECRET_KEY} — секретный ключ для JWT токенов</li>
 *     <li>{@code TOKEN_EXPIRATION} — время жизни токена (мс)</li>
 *     <li>{@code ISSUER} — имя эмитента JWT</li>
 *     <li>{@code APP_BASE_URL} — базовый URL приложения</li>
 *     <li>{@code SMTP_HOST}, {@code SMTP_PORT} — SMTP сервер</li>
 *     <li>{@code SMTP_USERNAME}, {@code SMTP_PASSWORD} — учётные данные SMTP</li>
 *     <li>{@code FROM_EMAIL} — email отправителя</li>
 *     <li>{@code SMTP_SSL}, {@code SMTP_TLS} — настройки безопасности</li>
 * </ul>
 *
 * @see AbstractModule
 * @see Provides
 */
public class MailModule extends AbstractModule {

    /**
     * Конфигурирует привязки зависимостей для email сервиса.
     * <p>
     * Привязывает следующие именованные зависимости:
     * <ul>
     *     <li>{@code secret.key} — секретный ключ для JWT</li>
     *     <li>{@code token.expiration} — время жизни токена (86400000 мс = 24 часа)</li>
     *     <li>{@code issuer} — эмитент JWT (voice-reminder-app)</li>
     *     <li>{@code base.url} — базовый URL приложения</li>
     *     <li>{@code smtp.host}, {@code smtp.port} — SMTP сервер (smtp.gmail.com:587)</li>
     *     <li>{@code smtp.username}, {@code smtp.password} — учётные данные</li>
     *     <li>{@code smtp.from.email} — email отправителя</li>
     *     <li>{@code smtp.ssl}, {@code smtp.tls} — настройки безопасности</li>
     * </ul>
     */
    @Override
    public void configure() {
        bind(String.class).annotatedWith(Names.named("secret.key"))
                .toInstance(System.getenv().getOrDefault("SECRET_KEY", "default-secret-key"));
        bind(Long.class).annotatedWith(Names.named("token.expiration"))
                .toInstance(Long.parseLong(
                        System.getenv().getOrDefault("TOKEN_EXPIRATION", "86400000")));
        bind(String.class).annotatedWith(Names.named("issuer"))
                .toInstance(System.getenv().getOrDefault("ISSUER", "voice-reminder-app"));
        bind(String.class).annotatedWith(Names.named("base.url"))
                .toInstance(System.getenv().getOrDefault("APP_BASE_URL", "http://localhost:8090"));
        bind(String.class).annotatedWith(Names.named("smtp.host"))
                .toInstance(System.getenv().getOrDefault("SMTP_HOST", "smtp.gmail.com"));
        bind(Integer.class).annotatedWith(Names.named("smtp.port"))
                .toInstance(Integer.parseInt(
                        System.getenv().getOrDefault("SMTP_PORT", "587")));
        bind(String.class).annotatedWith(Names.named("smtp.username"))
                .toInstance(System.getenv().getOrDefault("SMTP_USERNAME", "losik2006@gmail.com"));
        bind(String.class).annotatedWith(Names.named("smtp.password"))
                .toInstance(System.getenv().getOrDefault("SMTP_PASSWORD", "your-app-password"));
        bind(String.class).annotatedWith(Names.named("smtp.from.email"))
                .toInstance(System.getenv().getOrDefault("FROM_EMAIL",
                        System.getenv().getOrDefault("SMTP_USERNAME", "losik2006@gmail.com")));
        bind(Boolean.class).annotatedWith(Names.named("smtp.ssl"))
                .toInstance(Boolean.parseBoolean(System.getenv().getOrDefault("SMTP_SSL", "false")));
        bind(Boolean.class).annotatedWith(Names.named("smtp.tls"))
                .toInstance(Boolean.parseBoolean(System.getenv().getOrDefault("SMTP_TLS", "true")));
    }

    /**
     * Создаёт и предоставляет {@link EmailConfig} для отправки email уведомлений.
     * <p>
     * EmailConfig используется для:
     * <ul>
     *     <li>Отправки напоминаний пользователям</li>
     *     <li>Отправки токенов сброса пароля</li>
     *     <li>Отправки подтверждений изменения пароля</li>
     * </ul>
     *
     * @param smtpHost SMTP сервер (например, smtp.gmail.com)
     * @param smtpPort порт SMTP сервера (587 для TLS)
     * @param smtpUsername имя пользователя SMTP
     * @param smtpPassword пароль SMTP
     * @param fromEmail email отправителя
     * @param useSsl использовать ли SSL
     * @param useTls использовать ли TLS
     * @return настроенный EmailConfig
     * @see EmailConfig
     */
    @Provides
    @Singleton
    public EmailConfig provideEmailConfig(
            @Named("smtp.host") String smtpHost,
            @Named("smtp.port") int smtpPort,
            @Named("smtp.username") String smtpUsername,
            @Named("smtp.password") String smtpPassword,
            @Named("smtp.from.email") String fromEmail,
            @Named("smtp.ssl") boolean useSsl,
            @Named("smtp.tls") boolean useTls) {

        int connectionTimeout = Integer.parseInt(
                System.getenv().getOrDefault("EMAIL_CONNECTION_TIMEOUT_MS", "5000"));
        int writeTimeout = Integer.parseInt(
                System.getenv().getOrDefault("EMAIL_WRITE_TIMEOUT_MS", "10000"));
        int poolSize = Integer.parseInt(
                System.getenv().getOrDefault("EMAIL_THREAD_POOL_SIZE", "5"));

        return new EmailConfig(smtpHost, smtpPort, smtpUsername,
                smtpPassword, fromEmail, useSsl, useTls,
                connectionTimeout, writeTimeout, poolSize);
    }
}