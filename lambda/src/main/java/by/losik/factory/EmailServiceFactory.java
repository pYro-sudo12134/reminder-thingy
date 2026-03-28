package by.losik.factory;

import by.losik.config.EmailConfig;
import by.losik.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

import java.util.HashMap;
import java.util.Map;

public class EmailServiceFactory {
    private static final Logger log = LoggerFactory.getLogger(EmailServiceFactory.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_SMTP_PORT = 587;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 5000;
    private static final int DEFAULT_WRITE_TIMEOUT = 10000;
    private static final int DEFAULT_THREAD_POOL_SIZE = 5;
    private static final boolean DEFAULT_USE_TLS = true;
    private static final boolean DEFAULT_USE_SSL = false;
    private static final String ENV_SMTP_HOST = "SMTP_HOST";
    private static final String ENV_SMTP_PORT = "SMTP_PORT";
    private static final String ENV_SMTP_USERNAME = "SMTP_USERNAME";
    private static final String ENV_SMTP_PASSWORD = "SMTP_PASSWORD";
    private static final String ENV_FROM_EMAIL = "FROM_EMAIL";
    private static final String ENV_SMTP_SSL = "SMTP_SSL";
    private static final String ENV_SMTP_TLS = "SMTP_TLS";
    private static final String ENV_CONNECTION_TIMEOUT = "SMTP_CONNECTION_TIMEOUT";
    private static final String ENV_WRITE_TIMEOUT = "SMTP_WRITE_TIMEOUT";
    private static final String ENV_THREAD_POOL_SIZE = "EMAIL_THREAD_POOL_SIZE";
    private static final String ENV_SECRET_NAME = "SMTP_SECRET_NAME";
    private static final String ENV_AWS_REGION = "AWS_REGION";
    private static final String ENV_USE_SECRETS_MANAGER = "USE_SECRETS_MANAGER";
    private static final String ENV_USE_PARAMETER_STORE = "USE_PARAMETER_STORE";

    /**
     * Создает EmailService с конфигурацией из доступных источников.
     * Приоритет: Secrets Manager > Parameter Store > Environment Variables
     */
    public static EmailService create() {
        Map<String, String> config = new HashMap<>();

        if (shouldUseSecretsManager()) {
            Map<String, String> secretsConfig = loadFromSecretsManager();
            if (secretsConfig != null && !secretsConfig.isEmpty()) {
                config.putAll(secretsConfig);
                log.info("Loaded email configuration from AWS Secrets Manager");
            }
        }

        if (config.isEmpty() && shouldUseParameterStore()) {
            Map<String, String> paramStoreConfig = loadFromParameterStore();
            if (!paramStoreConfig.isEmpty()) {
                config.putAll(paramStoreConfig);
                log.info("Loaded email configuration from AWS Parameter Store");
            }
        }

        loadFromEnvironment(config);

        validateConfig(config);

        EmailConfig emailConfig = buildEmailConfig(config);

        return new EmailService(emailConfig);
    }

    /**
     * Определяет, нужно ли использовать Secrets Manager
     */
    private static boolean shouldUseSecretsManager() {
        String useSecretsManager = System.getenv(ENV_USE_SECRETS_MANAGER);
        String secretName = System.getenv(ENV_SECRET_NAME);

        return (Boolean.parseBoolean(useSecretsManager)) ||
                (secretName != null && !secretName.isEmpty());
    }

    /**
     * Определяет, нужно ли использовать Parameter Store
     */
    private static boolean shouldUseParameterStore() {
        String useParameterStore = System.getenv(ENV_USE_PARAMETER_STORE);
        return Boolean.parseBoolean(useParameterStore);
    }

    /**
     * Загружает конфигурацию из AWS Secrets Manager
     */
    private static Map<String, String> loadFromSecretsManager() {
        String secretName = System.getenv(ENV_SECRET_NAME);
        String region = System.getenv(ENV_AWS_REGION);

        if (secretName == null || secretName.isEmpty()) {
            log.warn("SMTP_SECRET_NAME not set, skipping Secrets Manager");
            return new HashMap<>();
        }

        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(region != null ? Region.of(region) : Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse response = client.getSecretValue(request);
            String secretString = response.secretString();

            if (secretString != null && !secretString.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, String> secrets = objectMapper.readValue(secretString, Map.class);
                return secrets;
            }

        } catch (SecretsManagerException e) {
            log.error("Failed to load secret from Secrets Manager: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error parsing secret from Secrets Manager", e);
        }

        return new HashMap<>();
    }

    /**
     * Загружает конфигурацию из AWS Parameter Store
     */
    private static Map<String, String> loadFromParameterStore() {
        String region = System.getenv(ENV_AWS_REGION);
        Map<String, String> config = new HashMap<>();

        try (SsmClient ssmClient = SsmClient.builder()
                .region(region != null ? Region.of(region) : Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            String[] paramPaths = {
                    "/voice-reminder/smtp/host",
                    "/voice-reminder/smtp/port",
                    "/voice-reminder/smtp/username",
                    "/voice-reminder/smtp/password",
                    "/voice-reminder/smtp/from-email",
                    "/voice-reminder/smtp/ssl",
                    "/voice-reminder/smtp/tls",
                    "/voice-reminder/smtp/connection-timeout",
                    "/voice-reminder/smtp/write-timeout",
                    "/voice-reminder/smtp/thread-pool-size"
            };

            for (String paramPath : paramPaths) {
                try {
                    GetParameterRequest request = GetParameterRequest.builder()
                            .name(paramPath)
                            .withDecryption(true)
                            .build();

                    GetParameterResponse response = ssmClient.getParameter(request);
                    String paramName = paramPath.substring(paramPath.lastIndexOf('/') + 1);
                    String paramValue = response.parameter().value();

                    config.put(toEnvVarName(paramName), paramValue);

                } catch (SsmException e) {
                    log.debug("Parameter {} not found in Parameter Store", paramPath);
                }
            }

        } catch (Exception e) {
            log.error("Failed to load parameters from Parameter Store", e);
        }

        return config;
    }

    /**
     * Загружает конфигурацию из переменных окружения
     */
    private static void loadFromEnvironment(Map<String, String> config) {
        // Заполняем недостающие значения из переменных окружения
        putIfAbsent(config, ENV_SMTP_HOST, System.getenv(ENV_SMTP_HOST));
        putIfAbsent(config, ENV_SMTP_PORT, System.getenv(ENV_SMTP_PORT));
        putIfAbsent(config, ENV_SMTP_USERNAME, System.getenv(ENV_SMTP_USERNAME));
        putIfAbsent(config, ENV_SMTP_PASSWORD, System.getenv(ENV_SMTP_PASSWORD));
        putIfAbsent(config, ENV_FROM_EMAIL, System.getenv(ENV_FROM_EMAIL));
        putIfAbsent(config, ENV_SMTP_SSL, System.getenv(ENV_SMTP_SSL));
        putIfAbsent(config, ENV_SMTP_TLS, System.getenv(ENV_SMTP_TLS));
        putIfAbsent(config, ENV_CONNECTION_TIMEOUT, System.getenv(ENV_CONNECTION_TIMEOUT));
        putIfAbsent(config, ENV_WRITE_TIMEOUT, System.getenv(ENV_WRITE_TIMEOUT));
        putIfAbsent(config, ENV_THREAD_POOL_SIZE, System.getenv(ENV_THREAD_POOL_SIZE));
    }

    /**
     * Валидирует обязательные параметры конфигурации
     */
    private static void validateConfig(Map<String, String> config) {
        if (!config.containsKey(ENV_SMTP_HOST) || config.get(ENV_SMTP_HOST).isEmpty()) {
            throw new IllegalStateException("SMTP_HOST is required");
        }
        if (!config.containsKey(ENV_SMTP_USERNAME) || config.get(ENV_SMTP_USERNAME).isEmpty()) {
            throw new IllegalStateException("SMTP_USERNAME is required");
        }
        if (!config.containsKey(ENV_SMTP_PASSWORD) || config.get(ENV_SMTP_PASSWORD).isEmpty()) {
            throw new IllegalStateException("SMTP_PASSWORD is required");
        }
    }

    /**
     * Создает EmailConfig из мапы с конфигурацией
     */
    private static EmailConfig buildEmailConfig(Map<String, String> config) {
        String smtpHost = config.get(ENV_SMTP_HOST);
        int smtpPort = parseIntOrDefault(config.get(ENV_SMTP_PORT), DEFAULT_SMTP_PORT);
        String smtpUsername = config.get(ENV_SMTP_USERNAME);
        String smtpPassword = config.get(ENV_SMTP_PASSWORD);
        String fromEmail = config.getOrDefault(ENV_FROM_EMAIL, smtpUsername);
        boolean useSsl = parseBooleanOrDefault(config.get(ENV_SMTP_SSL), DEFAULT_USE_SSL);
        boolean useTls = parseBooleanOrDefault(config.get(ENV_SMTP_TLS), DEFAULT_USE_TLS);
        int connectionTimeout = parseIntOrDefault(config.get(ENV_CONNECTION_TIMEOUT), DEFAULT_CONNECTION_TIMEOUT);
        int writeTimeout = parseIntOrDefault(config.get(ENV_WRITE_TIMEOUT), DEFAULT_WRITE_TIMEOUT);
        int threadPoolSize = parseIntOrDefault(config.get(ENV_THREAD_POOL_SIZE), DEFAULT_THREAD_POOL_SIZE);

        return new EmailConfig(
                smtpHost, smtpPort, smtpUsername, smtpPassword,
                fromEmail, useSsl, useTls,
                connectionTimeout, writeTimeout, threadPoolSize
        );
    }

    /**
     * Вспомогательные методы
     */
    private static void putIfAbsent(Map<String, String> map, String key, String value) {
        if (!map.containsKey(key) && value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value: {}, using default: {}", value, defaultValue);
            return defaultValue;
        }
    }

    private static boolean parseBooleanOrDefault(String value, boolean defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private static String toEnvVarName(String paramName) {
        return paramName.toUpperCase().replace('-', '_');
    }
}