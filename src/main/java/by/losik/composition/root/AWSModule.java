package by.losik.composition.root;

import by.losik.config.GRPCConfig;
import by.losik.config.LocalStackConfig;
import by.losik.config.MonitoringConfig;
import by.losik.config.SecretsManagerConfig;
import by.losik.filter.RateLimiterFilter;
import by.losik.resource.AuthResource;
import by.losik.resource.MetricsResource;
import by.losik.resource.PasswordResetResource;
import by.losik.resource.ReminderResource;
import by.losik.server.WebServer;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.util.Optional;

public class AWSModule extends AbstractModule {
    String endpoint = Optional.ofNullable(System.getenv("AWS_ENDPOINT_URL"))
            .or(() -> Optional.ofNullable(System.getProperty("AWS_ENDPOINT_URL")))
            .orElse("http://localstack:4566");

    String region = Optional.ofNullable(System.getenv("AWS_REGION"))
            .or(() -> Optional.ofNullable(System.getProperty("AWS_REGION")))
            .orElse("us-east-1");

    String accessKey = Optional.ofNullable(System.getenv("AWS_ACCESS_KEY_ID"))
            .or(() -> Optional.ofNullable(System.getProperty("AWS_ACCESS_KEY")))
            .orElse("test");

    String secretKey = Optional.ofNullable(System.getenv("AWS_SECRET_ACCESS_KEY"))
            .or(() -> Optional.ofNullable(System.getProperty("AWS_SECRET_KEY")))
            .orElse("test");

    String email = Optional.ofNullable(System.getenv("EMAIL"))
            .or(() -> Optional.ofNullable(System.getProperty("USER_EMAIL")))
            .orElse("losik2006@gmail.com");

    String openSearchPort = Optional.ofNullable(System.getenv("OPENSEARCH_PORT"))
            .or(() -> Optional.ofNullable(System.getProperty("OPENSEARCH_PORT")))
            .orElse("4510");

    String openSearchHost = Optional.ofNullable(System.getenv("OPENSEARCH_HOST"))
            .or(() -> Optional.ofNullable(System.getProperty("OPENSEARCH_HOST")))
            .orElse("localstack");

    String environmentName = Optional.ofNullable(System.getenv("ENVIRONMENT_NAME"))
            .or(() -> Optional.ofNullable(System.getProperty("ENVIRONMENT")))
            .orElse("dev");

    String truststorePath = Optional.ofNullable(System.getenv("OPENSEARCH_TRUSTSTORE_PATH"))
            .or(() -> Optional.ofNullable(System.getProperty("OPENSEARCH_TRUSTSTORE_PATH")))
            .orElse("/app/certs/truststore.jks");

    String truststorePassword = Optional.ofNullable(System.getenv("OPENSEARCH_TRUSTSTORE_PASSWORD"))
            .or(() -> Optional.ofNullable(System.getProperty("OPENSEARCH_TRUSTSTORE_PASSWORD")))
            .orElse("changeit");

    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    public LocalStackConfig createLocalStackConfig(SecretsManagerConfig secretsManagerConfig) {

        return new LocalStackConfig(endpoint, region, accessKey, secretKey,
                email, openSearchPort, openSearchHost,
                truststorePath, truststorePassword,
                secretsManagerConfig);
    }

    @Provides
    @Singleton
    public SecretsManagerConfig createSecretsManagerConfig() {

        return new SecretsManagerConfig(
                endpoint,
                region,
                accessKey,
                secretKey,
                environmentName
        );
    }

    @Provides
    @Singleton
    public GRPCConfig createGRPCConfig(SecretsManagerConfig secretsManagerConfig) {
        return new GRPCConfig(secretsManagerConfig);
    }

    @Provides
    @Singleton
    public WebServer createWebServer(ReminderResource reminderResource,
                                     AuthResource authResource,
                                     MetricsResource metricsResource,
                                     PasswordResetResource passwordResetResource,
                                     RateLimiterFilter rateLimiterFilter) {
        String portStr = Optional.ofNullable(System.getenv("WS_PORT"))
                .or(() -> Optional.ofNullable(System.getProperty("WS_PORT")))
                .orElse("8090");

        int webServerPort;
        try {
            webServerPort = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            webServerPort = 8090;
        }

        return new WebServer(webServerPort, reminderResource, authResource, metricsResource, passwordResetResource, rateLimiterFilter);
    }

    @Provides
    @Singleton
    public MonitoringConfig createCloudWatchConfigForLocalstack(LocalStackConfig localStackConfig,
                                                                 SecretsManagerConfig secretsManagerConfig) {
        return new MonitoringConfig(localStackConfig, secretsManagerConfig);
    }


}