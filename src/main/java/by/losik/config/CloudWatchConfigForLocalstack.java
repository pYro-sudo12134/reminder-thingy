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

import java.time.Duration;
import java.util.Map;

@Singleton
public class CloudWatchConfigForLocalstack implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(CloudWatchConfigForLocalstack.class);
    private final LocalStackConfig localStackConfig;
    private MeterRegistry meterRegistry;

    @Inject
    public CloudWatchConfigForLocalstack(LocalStackConfig localStackConfig) {
        this.localStackConfig = localStackConfig;
        initMetrics();
    }

    private void initMetrics() {
        try {
            CloudWatchAsyncClient cloudWatchAsyncClient = localStackConfig.getCloudWatchAsyncClient();

            CloudWatchConfig cloudWatchConfig = new CloudWatchConfig() {
                private final Map<String, String> configuration = Map.of(
                        "cloudwatch.namespace", "VoiceReminderApp",
                        "cloudwatch.step", Duration.ofMinutes(1).toString()
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

            new ClassLoaderMetrics().bindTo(meterRegistry);
            new JvmMemoryMetrics().bindTo(meterRegistry);
            new JvmGcMetrics().bindTo(meterRegistry);
            new ProcessorMetrics().bindTo(meterRegistry);
            new JvmThreadMetrics().bindTo(meterRegistry);

            log.info("Micrometer CloudWatch registry initialized");

        } catch (Exception e) {
            log.error("Failed to initialize CloudWatch metrics", e);
            meterRegistry = null;
        }
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