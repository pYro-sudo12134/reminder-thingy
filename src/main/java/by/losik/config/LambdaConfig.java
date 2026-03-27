package by.losik.config;

import by.losik.util.ConfigUtils;
import com.google.inject.Singleton;

/**
 * Конфигурация AWS Lambda функций.
 * <p>
 * Определяет настройки для Lambda функций:
 * <ul>
 *     <li>Имя функции и ARN роли</li>
 *     <li>Класс handler и путь к JAR файлу</li>
 *     <li>Таймаут и размер памяти</li>
 *     <li>Настройки логирования</li>
 * </ul>
 * <p>
 * Настройки загружаются из переменных окружения через ConfigUtils:
 * <ul>
 *     <li>{@code LAMBDA_FUNCTION_NAME} — имя функции (по умолчанию "send-reminder")</li>
 *     <li>{@code LAMBDA_ROLE_ARN} — ARN IAM роли (по умолчанию "arn:aws:iam::000000000000:role/lambda-role")</li>
 *     <li>{@code LAMBDA_HANDLER_CLASS} — класс handler (по умолчанию "by.losik.lambda.SendReminderLambda::handleRequest")</li>
 *     <li>{@code LAMBDA_JAR_PATH} — путь к JAR файлу (по умолчанию "build/libs/send-reminder-lambda.jar")</li>
 *     <li>{@code LAMBDA_TIMEOUT_SEC} — таймаут в секундах (по умолчанию 30)</li>
 *     <li>{@code LAMBDA_MEMORY_SIZE} — размер памяти в MB (по умолчанию 512)</li>
 *     <li>{@code LAMBDA_DEPLOY_ENABLED} — включить деплой (по умолчанию true)</li>
 *     <li>{@code LAMBDA_SEND_REMINDER_TIMEOUT_SEC} — таймаут на отправку напоминания (по умолчанию 30)</li>
 *     <li>{@code LAMBDA_DETAILED_LOGGING} — детальное логирование (по умолчанию true)</li>
 * </ul>
 *
 * @see by.losik.lambda.SendReminderLambda
 * @see by.losik.service.LambdaHandler
 */
@Singleton
public class LambdaConfig {

    /** Имя Lambda функции по умолчанию */
    private static final String DEFAULT_FUNCTION_NAME = "send-reminder";

    /** ARN IAM роли по умолчанию */
    private static final String DEFAULT_ROLE_ARN = "arn:aws:iam::000000000000:role/lambda-role";

    /** Класс handler по умолчанию */
    private static final String DEFAULT_HANDLER_CLASS = "by.losik.lambda.SendReminderLambda::handleRequest";

    /** Путь к JAR файлу по умолчанию */
    private static final String DEFAULT_JAR_PATH = "build/libs/send-reminder-lambda.jar";

    /** Таймаут на отправку напоминания по умолчанию (30 секунд) */
    private static final long DEFAULT_SEND_REMINDER_TIMEOUT_SEC = 30L;

    /** Детальное логирование по умолчанию (включено) */
    private static final boolean DEFAULT_DETAILED_LOGGING = true;

    private final String functionName;
    private final String roleArn;
    private final String handlerClass;
    private final String jarPath;
    private final int timeoutSec;
    private final int memorySize;
    private final boolean deployEnabled;
    private final long sendReminderTimeoutSec;
    private final boolean enableDetailedLogging;

    /**
     * Создаёт конфигурацию Lambda с загрузкой настроек из переменных окружения.
     */
    public LambdaConfig() {
        this.functionName = ConfigUtils.getEnvOrDefault("LAMBDA_FUNCTION_NAME", DEFAULT_FUNCTION_NAME);
        this.roleArn = ConfigUtils.getEnvOrDefault("LAMBDA_ROLE_ARN", DEFAULT_ROLE_ARN);
        this.handlerClass = ConfigUtils.getEnvOrDefault("LAMBDA_HANDLER_CLASS", DEFAULT_HANDLER_CLASS);
        this.jarPath = ConfigUtils.getEnvOrDefault("LAMBDA_JAR_PATH", DEFAULT_JAR_PATH);
        this.timeoutSec = ConfigUtils.getIntEnvOrDefault("LAMBDA_TIMEOUT_SEC", 30);
        this.memorySize = ConfigUtils.getIntEnvOrDefault("LAMBDA_MEMORY_SIZE", 512);
        this.deployEnabled = ConfigUtils.getBooleanEnvOrDefault("LAMBDA_DEPLOY_ENABLED", true);
        this.sendReminderTimeoutSec = ConfigUtils.getLongEnvOrDefault("LAMBDA_SEND_REMINDER_TIMEOUT_SEC", DEFAULT_SEND_REMINDER_TIMEOUT_SEC);
        this.enableDetailedLogging = ConfigUtils.getBooleanEnvOrDefault("LAMBDA_DETAILED_LOGGING", DEFAULT_DETAILED_LOGGING);
    }

    /**
     * Получает имя Lambda функции.
     *
     * @return имя функции (по умолчанию "send-reminder")
     */
    public String getFunctionName() {
        return functionName;
    }

    /**
     * Получает ARN IAM роли для Lambda.
     *
     * @return ARN роли
     */
    public String getRoleArn() {
        return roleArn;
    }

    /**
     * Получает класс handler для Lambda.
     *
     * @return класс handler (например, "by.losik.lambda.SendReminderLambda::handleRequest")
     */
    public String getHandlerClass() {
        return handlerClass;
    }

    /**
     * Получает путь к JAR файлу Lambda.
     *
     * @return путь к JAR файлу (например, "build/libs/send-reminder-lambda.jar")
     */
    public String getJarPath() {
        return jarPath;
    }

    /**
     * Получает таймаут выполнения Lambda в секундах.
     *
     * @return таймаут в секундах
     */
    public int getTimeoutSec() {
        return timeoutSec;
    }

    /**
     * Получает размер памяти для Lambda в MB.
     *
     * @return размер памяти в MB
     */
    public int getMemorySize() {
        return memorySize;
    }

    /**
     * Проверяет, включён ли деплой Lambda при старте приложения.
     *
     * @return true если деплой включён
     */
    public boolean isDeployEnabled() {
        return deployEnabled;
    }

    /**
     * Получает таймаут на отправку напоминания в секундах.
     *
     * @return таймаут в секундах (по умолчанию 30)
     */
    public long getSendReminderTimeoutSec() {
        return sendReminderTimeoutSec;
    }

    /**
     * Проверяет, включено ли детальное логирование.
     *
     * @return true если детальное логирование включено
     */
    public boolean isEnableDetailedLogging() {
        return enableDetailedLogging;
    }
}
