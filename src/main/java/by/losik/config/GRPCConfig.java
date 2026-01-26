package by.losik.config;

import com.google.inject.Singleton;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@Singleton
public class GRPCConfig {
    private final String nlpServiceHost;
    private final int nlpServicePort;
    private ManagedChannel channel;

    public GRPCConfig(String nlpServiceHost, Integer nlpServicePort) {
        this.nlpServiceHost = nlpServiceHost;
        this.nlpServicePort = nlpServicePort;
    }

    public ManagedChannel getChannel() {
        if (channel == null || channel.isShutdown() || channel.isTerminated()) {
            synchronized (this) {
                if (channel == null || channel.isShutdown() || channel.isTerminated()) {
                    channel = ManagedChannelBuilder.forAddress(nlpServiceHost, nlpServicePort)
                            .usePlaintext()
                            .maxInboundMessageSize(100 * 1024 * 1024)
                            .keepAliveTime(30, java.util.concurrent.TimeUnit.SECONDS)
                            .keepAliveTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return channel;
    }

    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
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