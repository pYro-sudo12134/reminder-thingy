package by.losik.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class RateLimitConfig {
    private final SecretsManagerConfig secretsManager;

    @Inject
    public RateLimitConfig(SecretsManagerConfig secretsManager) {
        this.secretsManager = secretsManager;
    }

    public int getMaxRequestsPerMinute() {
        return Integer.parseInt(
                secretsManager.getSecret("RATE_LIMIT_PER_MINUTE", "60")
        );
    }

    public int getMaxRequestsPerHour() {
        return Integer.parseInt(
                secretsManager.getSecret("RATE_LIMIT_PER_HOUR", "1000")
        );
    }

    public int getMaxRequestsPerDay() {
        return Integer.parseInt(
                secretsManager.getSecret("RATE_LIMIT_PER_DAY", "5000")
        );
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(
                secretsManager.getSecret("RATE_LIMIT_ENABLED", "true")
        );
    }

    public int getUploadLimitPerMinute() {
        return Integer.parseInt(
                secretsManager.getSecret("RATE_LIMIT_UPLOAD_PER_MINUTE", "10")
        );
    }

    public int getApiLimitPerMinute() {
        return Integer.parseInt(
                secretsManager.getSecret("RATE_LIMIT_API_PER_MINUTE", "60")
        );
    }

    public String[] getWhitelistedIps() {
        String whitelist = secretsManager.getSecret("RATE_LIMIT_WHITELIST", "");
        if (whitelist == null || whitelist.isBlank()) {
            return new String[0];
        }
        return whitelist.split(",");
    }
}