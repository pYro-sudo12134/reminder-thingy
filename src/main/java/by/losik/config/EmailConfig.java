package by.losik.config;

/**
 * Конфигурация SMTP сервера для отправки email уведомлений.
 * <p>
 * Используется для:
 * <ul>
 *     <li>Отправки напоминаний пользователям</li>
 *     <li>Отправки токенов сброса пароля</li>
 *     <li>Отправки подтверждений изменения пароля</li>
 * </ul>
 * <p>
 * Настройки загружаются из переменных окружения в
 * {@link by.losik.composition.root.MailModule}.
 *
 * @see by.losik.composition.root.MailModule
 * @see by.losik.service.EmailService
 */
public class EmailConfig {
    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String fromEmail;
    private final boolean useSsl;
    private final boolean useTls;

    /**
     * Создаёт конфигурацию email сервиса.
     *
     * @param smtpHost SMTP сервер (например, smtp.gmail.com)
     * @param smtpPort порт SMTP сервера (587 для TLS, 465 для SSL)
     * @param smtpUsername имя пользователя для аутентификации
     * @param smtpPassword пароль для аутентификации
     * @param fromEmail email отправителя
     * @param useSsl использовать ли SSL
     * @param useTls использовать ли TLS
     */
    public EmailConfig(String smtpHost, int smtpPort, String smtpUsername,
                       String smtpPassword, String fromEmail, boolean useSsl, boolean useTls) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.fromEmail = fromEmail;
        this.useSsl = useSsl;
        this.useTls = useTls;
    }
    
    /**
     * Получает хост SMTP сервера.
     * @return хост SMTP (например, smtp.gmail.com)
     */
    public String getSmtpHost() { return smtpHost; }
    
    /**
     * Получает порт SMTP сервера.
     * @return порт (587 для TLS, 465 для SSL)
     */
    public int getSmtpPort() { return smtpPort; }
    
    /**
     * Получает имя пользователя SMTP.
     * @return имя пользователя
     */
    public String getSmtpUsername() { return smtpUsername; }
    
    /**
     * Получает пароль SMTP.
     * @return пароль
     */
    public String getSmtpPassword() { return smtpPassword; }
    
    /**
     * Получает email отправителя.
     * @return email отправителя (from address)
     */
    public String getFromEmail() { return fromEmail; }
    
    /**
     * Проверяет, включён ли SSL.
     * @return true если SSL включён
     */
    public boolean isUseSsl() { return useSsl; }
    
    /**
     * Проверяет, включён ли TLS.
     * @return true если TLS включён
     */
    public boolean isUseTls() { return useTls; }
}