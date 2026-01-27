package by.losik.composition.root;

import by.losik.config.CloudWatchConfigForLocalstack;
import by.losik.config.GRPCConfig;
import by.losik.config.LocalStackConfig;
import by.losik.resource.ReminderResource;
import by.losik.server.WebServer;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.util.Optional;

public class CompositionRoot extends AbstractModule {

    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    private LocalStackConfig createLocalStackConfig() {
        String endpoint = Optional.ofNullable(System.getenv("LOCALSTACK_ENDPOINT"))
                .or(() -> Optional.ofNullable(System.getProperty("LOCALSTACK_ENDPOINT")))
                .orElse("http://localhost:4566");

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

        String openSearchHost = Optional.ofNullable(System.getenv("OPENSEARCH_PORT"))
                .or(() -> Optional.ofNullable(System.getProperty("OPENSEARCH_PORT")))
                .orElse("4510");

        return new LocalStackConfig(endpoint, region, accessKey, secretKey, email, openSearchPort, openSearchHost);
    }

    @Provides
    @Singleton
    private GRPCConfig createGRPCConfig() {
        String nlpServiceHost = Optional.ofNullable(System.getenv("NLP_SERVICE_HOST"))
                .or(() -> Optional.ofNullable(System.getProperty("NLP_SERVICE_HOST")))
                .orElse("localhost");

        String portStr = Optional.ofNullable(System.getenv("NLP_SERVICE_PORT"))
                .or(() -> Optional.ofNullable(System.getProperty("NLP_SERVICE_PORT")))
                .orElse("50051");

        int nlpServicePort;
        try {
            nlpServicePort = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            nlpServicePort = 50051;
        }

        return new GRPCConfig(nlpServiceHost, nlpServicePort);
    }

    @Provides
    @Singleton
    private WebServer createWebServer(ReminderResource reminderResource) {
        String portStr = Optional.ofNullable(System.getenv("WS_PORT"))
                .or(() -> Optional.ofNullable(System.getProperty("WS_PORT")))
                .orElse("8090");

        int webServerPort;
        try {
            webServerPort = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            webServerPort = 8090;
        }

        return new WebServer(webServerPort, reminderResource);
    }

    @Provides
    @Singleton
    private CloudWatchConfigForLocalstack createCloudWatchConfigForLocalstack(LocalStackConfig localStackConfig) {
        return new CloudWatchConfigForLocalstack(localStackConfig);
    }
}