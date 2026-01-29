package by.losik.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.common.lang.NonNull;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.util.Map;

@Singleton
public class MonitoringConfig implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MonitoringConfig.class);
    private final LocalStackConfig localStackConfig;
    private final SecretsManagerConfig secretsManagerConfig;
    private MeterRegistry meterRegistry;

    @Inject
    public MonitoringConfig(LocalStackConfig localStackConfig,
                            SecretsManagerConfig secretsManagerConfig) {
        this.localStackConfig = localStackConfig;
        this.secretsManagerConfig = secretsManagerConfig;
        initMetrics();
    }

    private void initMetrics() {
        try {
            CloudWatchAsyncClient cloudWatchAsyncClient = localStackConfig.getCloudWatchAsyncClient();

            String namespace = secretsManagerConfig.getSecret("CLOUDWATCH_NAMESPACE",
                    "VoiceReminderApp");
            String step = secretsManagerConfig.getSecret("METRICS_STEP", "PT1M");

            CloudWatchConfig cloudWatchConfig = new CloudWatchConfig() {
                private final Map<String, String> configuration = Map.of(
                        "cloudwatch.namespace", namespace,
                        "cloudwatch.step", step
                );

                @Override
                public String get(@NonNull String key) {
                    return configuration.get(key);
                }
            };

            meterRegistry = new CloudWatchMeterRegistry(
                    cloudWatchConfig,
                    Clock.SYSTEM,
                    cloudWatchAsyncClient
            );

            registerMetrics();

            log.info("Micrometer CloudWatch registry initialized with namespace: {}", namespace);

        } catch (Exception e) {
            log.error("Failed to initialize CloudWatch metrics", e);
            meterRegistry = null;
        }
    }

    private void registerMetrics() {
        new ClassLoaderMetrics().bindTo(meterRegistry);
        new JvmMemoryMetrics().bindTo(meterRegistry);
        new JvmGcMetrics().bindTo(meterRegistry);
        new ProcessorMetrics().bindTo(meterRegistry);
        new JvmThreadMetrics().bindTo(meterRegistry);
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    public boolean isMetricsEnabled() {
        return meterRegistry != null;
    }

    @Override
    public void close() {
        if (meterRegistry != null) {
            try {
                meterRegistry.close();
                log.info("Metrics registry closed");
            } catch (Exception e) {
                log.warn("Error closing metrics registry", e);
            }
        }
    }
}