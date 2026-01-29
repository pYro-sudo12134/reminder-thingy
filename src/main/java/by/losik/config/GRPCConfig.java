package by.losik.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import java.util.concurrent.TimeUnit;

@Singleton
public class GRPCConfig {
    private final SecretsManagerConfig secretsManager;
    private final String nlpServiceHost;
    private final int nlpServicePort;
    private final boolean useTLS;
    private ManagedChannel channel;

    @Inject
    public GRPCConfig(SecretsManagerConfig secretsManager) {
        this.secretsManager = secretsManager;

        this.nlpServiceHost = secretsManager.getSecret("NLP_SERVICE_HOST",
                java.util.Optional.ofNullable(System.getenv("NLP_SERVICE_HOST"))
                        .orElse("localhost"));

        String portStr = secretsManager.getSecret("NLP_SERVICE_PORT",
                java.util.Optional.ofNullable(System.getenv("NLP_SERVICE_PORT"))
                        .orElse("50051"));

        this.nlpServicePort = Integer.parseInt(portStr);
        this.useTLS = Boolean.parseBoolean(
                secretsManager.getSecret("GRPC_USE_TLS", "false"));
    }

    public ManagedChannel getChannel() {
        if (channel == null || channel.isShutdown() || channel.isTerminated()) {
            synchronized (this) {
                if (channel == null || channel.isShutdown() || channel.isTerminated()) {
                    ManagedChannelBuilder<?> builder = ManagedChannelBuilder
                            .forAddress(nlpServiceHost, nlpServicePort);

                    if (useTLS) {
                        builder.useTransportSecurity();
                    } else {
                        builder.usePlaintext();
                    }

                    String apiKey = secretsManager.getSecret("NLP_GRPC_API_KEY");
                    if (apiKey != null && !apiKey.isEmpty()) {
                        builder.intercept(createAuthInterceptor(apiKey));
                    }

                    channel = builder
                            .maxInboundMessageSize(100 * 1024 * 1024)
                            .keepAliveTime(30, TimeUnit.SECONDS)
                            .keepAliveTimeout(5, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return channel;
    }

    private io.grpc.ClientInterceptor createAuthInterceptor(String apiKey) {
        return MetadataUtils.newAttachHeadersInterceptor(
                createHeadersWithAuth(apiKey)
        );
    }

    private Metadata createHeadersWithAuth(String apiKey) {
        Metadata headers = new Metadata();
        Metadata.Key<String> authKey = Metadata.Key.of(
                "authorization", Metadata.ASCII_STRING_MARSHALLER
        );
        headers.put(authKey, "Bearer " + apiKey);

        Metadata.Key<String> serviceKey = Metadata.Key.of(
                "x-service-name", Metadata.ASCII_STRING_MARSHALLER
        );
        headers.put(serviceKey, "voice-reminder-service");

        return headers;
    }

    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public String getNlpServiceHost() {
        return nlpServiceHost;
    }

    public int getNlpServicePort() {
        return nlpServicePort;
    }

    public boolean isAvailable() {
        try {
            ManagedChannel testChannel = getChannel();
            return !testChannel.isShutdown() && !testChannel.isTerminated();
        } catch (Exception e) {
            return false;
        }
    }
}