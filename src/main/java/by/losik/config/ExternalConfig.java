package by.losik.config;

import by.losik.util.ConfigUtils;
import com.google.inject.Singleton;

@Singleton
public class ExternalConfig {
    private final String API_KEY;

    public ExternalConfig() {
        this.API_KEY = ConfigUtils.getEnvOrDefault("LAMBDA_API_KEY", "my-api-key");
    }

    public String getAPI_KEY() {
        return API_KEY;
    }
}
