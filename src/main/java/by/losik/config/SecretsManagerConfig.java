package by.losik.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class SecretsManagerConfig implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SecretsManagerConfig.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SecretsManagerClient secretsManagerClient;
    private final String secretName;
    private final Map<String, String> cachedSecrets = new ConcurrentHashMap<>();
    private final String environmentName;
    private final boolean useAwsSecretsManager;
    private final boolean useLocalFiles;

    public SecretsManagerConfig(String endpoint, String region, String accessKey,
                                String secretKey, String environmentName) {
        this.environmentName = environmentName;
        this.secretName = environmentName + "/voice-reminder/secrets";

        this.useAwsSecretsManager = isAwsSecretsManagerAvailable(endpoint, accessKey, secretKey);
        this.useLocalFiles = Boolean.parseBoolean(
                System.getenv().getOrDefault("USE_LOCAL_SECRETS", "true")
        );

        this.secretsManagerClient = useAwsSecretsManager ?
                SecretsManagerClient.builder()
                        .endpointOverride(endpoint != null ? URI.create(endpoint) : null)
                        .region(region != null ? Region.of(region) : Region.US_EAST_1)
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        ))
                        .build() : null;

        loadAllSecrets();
        logSecretsSource();
    }

    public String getSecret(String key) {
        return cachedSecrets.get(key);
    }

    public String getSecret(String key, String defaultValue) {
        return cachedSecrets.getOrDefault(key, defaultValue);
    }

    public <T> T getSecretAsObject(String key, Class<T> type) {
        try {
            String value = getSecret(key);
            if (value == null) return null;
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            log.error("Failed to parse secret {} as {}", key, type.getSimpleName(), e);
            return null;
        }
    }

    public <T> T getSecretAsObject(String key, TypeReference<T> typeReference) {
        try {
            String value = getSecret(key);
            if (value == null) return null;
            return objectMapper.readValue(value, typeReference);
        } catch (Exception e) {
            log.error("Failed to parse secret {} as {}", key, typeReference.getType(), e);
            return null;
        }
    }

    public void refreshSecrets() {
        cachedSecrets.clear();
        loadAllSecrets();
        logSecretsSource();
    }

    private void loadAllSecrets() {
        SecretSource source;

        if (loadFromDockerSecrets()) {
            source = SecretSource.DOCKER_SECRETS;
        } else if (useLocalFiles && loadFromLocalFiles()) {
            source = SecretSource.LOCAL_FILES;
        } else if (useAwsSecretsManager && loadFromAwsSecretsManager()) {
            source = SecretSource.AWS_SECRETS_MANAGER;
        } else {
            loadFromEnvironment();
            source = SecretSource.ENVIRONMENT;
        }

        log.info("Secrets loaded from source: {}", source);

        supplementFromEnvironment();
    }

    private boolean loadFromDockerSecrets() {
        try {
            Path dockerSecretsPath = Paths.get("/run/secrets");
            if (!Files.exists(dockerSecretsPath) || !Files.isDirectory(dockerSecretsPath)) {
                return false;
            }

            Map<String, String> dockerSecrets = new HashMap<>();

            Files.list(dockerSecretsPath)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            String key = file.getFileName().toString()
                                    .replace(".txt", "")
                                    .replace(".secret", "")
                                    .toUpperCase();
                            String value = Files.readString(file).trim();
                            dockerSecrets.put(key, value);
                            log.debug("Loaded Docker secret: {} ({} chars)", key, value.length());
                        } catch (IOException e) {
                            log.warn("Failed to read Docker secret file: {}", file, e);
                        }
                    });

            if (!dockerSecrets.isEmpty()) {
                cachedSecrets.putAll(dockerSecrets);
                log.info("Loaded {} secrets from Docker secrets", dockerSecrets.size());
                return true;
            }
        } catch (Exception e) {
            log.debug("Docker secrets not available: {}", e.getMessage());
        }

        return false;
    }

    private boolean loadFromLocalFiles() {
        try {
            String[] possiblePaths = {
                    "./secrets/",
                    "secrets/",
                    "../secrets/",
                    "/app/secrets/",
                    System.getProperty("user.dir") + "/secrets/"
            };

            for (String path : possiblePaths) {
                Path localPath = Paths.get(path);
                if (Files.exists(localPath) && Files.isDirectory(localPath)) {
                    return loadSecretsFromDirectory(localPath);
                }
            }

            return loadIndividualSecretFiles();
        } catch (Exception e) {
            log.debug("Local secret files not available: {}", e.getMessage());
        }

        return false;
    }

    private boolean loadSecretsFromDirectory(Path directory) throws IOException {
        Map<String, String> localSecrets = new HashMap<>();

        Path jsonFile = directory.resolve("secrets.json");
        if (Files.exists(jsonFile)) {
            try {
                String jsonContent = Files.readString(jsonFile);
                Map<String, String> jsonSecrets = objectMapper.readValue(
                        jsonContent, new TypeReference<>() {
                        }
                );
                localSecrets.putAll(jsonSecrets);
                log.debug("Loaded secrets from JSON file");
            } catch (Exception e) {
                log.warn("Failed to parse secrets.json: {}", e.getMessage());
            }
        }

        Files.list(directory)
                .filter(Files::isRegularFile)
                .filter(file -> !file.getFileName().toString().equals("secrets.json"))
                .forEach(file -> {
                    try {
                        String key = file.getFileName().toString()
                                .replace(".txt", "")
                                .replace(".secret", "")
                                .toUpperCase();
                        String value = Files.readString(file).trim();
                        localSecrets.put(key, value);
                        log.debug("Loaded local file secret: {}", key);
                    } catch (IOException e) {
                        log.warn("Failed to read local secret file: {}", file, e);
                    }
                });

        if (!localSecrets.isEmpty()) {
            cachedSecrets.putAll(localSecrets);
            log.info("Loaded {} secrets from local files", localSecrets.size());
            return true;
        }

        return false;
    }

    private boolean loadIndividualSecretFiles() {
        Map<String, String> secrets = new HashMap<>();
        boolean loadedAny = false;

        String[] secretFiles = {
                "NLP_GRPC_API_KEY", "JWT_SECRET", "DATABASE_PASSWORD",
                "SERVICE_TOKEN", "AWS_ACCESS_KEY", "AWS_SECRET_KEY"
        };

        for (String fileName : secretFiles) {
            String value = readSecretFile(fileName.toLowerCase() + ".txt");
            if (value != null) {
                secrets.put(fileName, value);
                loadedAny = true;
            }
        }

        if (loadedAny) {
            cachedSecrets.putAll(secrets);
            log.info("Loaded {} secrets from individual files", secrets.size());
        }

        return loadedAny;
    }

    private String readSecretFile(String fileName) {
        String[] possiblePaths = {
                "./" + fileName,
                "secrets/" + fileName,
                "../secrets/" + fileName,
                "/app/" + fileName
        };

        for (String path : possiblePaths) {
            try {
                Path filePath = Paths.get(path);
                if (Files.exists(filePath)) {
                    return Files.readString(filePath).trim();
                }
            } catch (IOException e) {
                log.error("Could not read from {}", path);
            }
        }

        return null;
    }

    private boolean loadFromAwsSecretsManager() {
        try {
            if (secretsManagerClient == null) {
                return false;
            }

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);

            if (response.secretString() != null) {
                parseAndCacheSecrets(response.secretString());
                log.info("Successfully loaded secrets from AWS Secrets Manager");
                return true;
            }
        } catch (SecretsManagerException e) {
            log.debug("AWS Secrets Manager not available: {}", e.getMessage());
        }

        return false;
    }

    private void parseAndCacheSecrets(String json) {
        try {
            Map<String, String> secrets = objectMapper.readValue(
                    json, new TypeReference<>() {
                    }
            );
            cachedSecrets.putAll(secrets);
            log.debug("Loaded {} secrets from Secrets Manager", secrets.size());
        } catch (Exception e) {
            log.error("Failed to parse secrets JSON", e);
        }
    }

    private void loadFromEnvironment() {
        Map<String, String> envSecrets = new HashMap<>();

        envSecrets.put("NLP_GRPC_API_KEY", getEnv("NLP_GRPC_API_KEY"));
        envSecrets.put("JWT_SECRET", getEnv("JWT_SECRET"));
        envSecrets.put("SERVICE_TOKEN", getEnv("SERVICE_TOKEN"));
        envSecrets.put("NLP_SERVICE_HOST", getEnv("NLP_SERVICE_HOST", "localhost"));
        envSecrets.put("NLP_SERVICE_PORT", getEnv("NLP_SERVICE_PORT", "50051"));
        envSecrets.put("GRPC_USE_TLS", getEnv("GRPC_USE_TLS", "false"));
        envSecrets.put("WS_PORT", getEnv("WS_PORT", "8090"));
        envSecrets.put("AWS_ACCESS_KEY_ID", getEnv("AWS_ACCESS_KEY_ID"));
        envSecrets.put("AWS_SECRET_ACCESS_KEY", getEnv("AWS_SECRET_ACCESS_KEY"));

        envSecrets.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .forEach(entry -> cachedSecrets.put(entry.getKey(), entry.getValue()));

        log.info("Loaded {} secrets from environment variables",
                envSecrets.values().stream().filter(Objects::nonNull).count());
    }

    private void supplementFromEnvironment() {
        Map<String, String> defaults = new HashMap<>();

        defaults.put("NLP_SERVICE_HOST", getEnv("NLP_SERVICE_HOST", "localhost"));
        defaults.put("NLP_SERVICE_PORT", getEnv("NLP_SERVICE_PORT", "50051"));
        defaults.put("GRPC_USE_TLS", getEnv("GRPC_USE_TLS", "false"));
        defaults.put("WS_PORT", getEnv("WS_PORT", "8090"));

        defaults.forEach((key, value) -> {
            if (!cachedSecrets.containsKey(key) && value != null) {
                cachedSecrets.put(key, value);
                log.debug("Supplemented missing secret from env: {}", key);
            }
        });
    }

    private boolean isAwsSecretsManagerAvailable(String endpoint, String accessKey, String secretKey) {
        if (accessKey == null || secretKey == null) {
            return false;
        }

        if (endpoint != null && endpoint.contains("localhost")) {
            log.info("Using local secrets mode (LocalStack detected)");
            return false;
        }

        return true;
    }

    private void logSecretsSource() {
        log.info("Secrets Summary");
        log.info("Environment: {}", environmentName);
        log.info("Total secrets loaded: {}", cachedSecrets.size());

        cachedSecrets.keySet().forEach(key -> {
            String value = cachedSecrets.get(key);
            String maskedValue = value != null ?
                    value.substring(0, Math.min(3, value.length())) + "..." : "null";
            log.debug("  {}: {}", key, maskedValue);
        });

    }

    private String getEnv(String key) {
        return Optional.ofNullable(System.getenv(key))
                .or(() -> Optional.ofNullable(System.getProperty(key)))
                .orElse(null);
    }

    private String getEnv(String key, String defaultValue) {
        return Optional.ofNullable(System.getenv(key))
                .or(() -> Optional.ofNullable(System.getProperty(key)))
                .orElse(defaultValue);
    }

    public Map<String, String> getAllSecrets() {
        return Map.copyOf(cachedSecrets);
    }

    public boolean isSecretAvailable(String key) {
        return cachedSecrets.containsKey(key);
    }

    public void putSecret(String key, String value) {
        cachedSecrets.put(key, value);
    }

    @Override
    public void close() {
        if (secretsManagerClient != null) {
            secretsManagerClient.close();
        }
    }
}