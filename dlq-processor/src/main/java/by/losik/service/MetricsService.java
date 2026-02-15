package by.losik.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class MetricsService {
    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final MeterRegistry cloudWatchRegistry;
    private final PrometheusMeterRegistry prometheusRegistry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    private final boolean enabled;

    @Inject
    public MetricsService(
            CloudWatchAsyncClient cloudWatchAsyncClient,
            @Named("environment") String environment,
            @Named("metrics.enabled") boolean enabled) {

        this.enabled = enabled;
        this.prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        if (enabled) {
            CloudWatchConfig cloudWatchConfig = key -> Map.of(
                    "cloudwatch.namespace", "VoiceReminder/DLQ",
                    "cloudwatch.step", "PT1M"
            ).get(key);

            this.cloudWatchRegistry = new CloudWatchMeterRegistry(
                    cloudWatchConfig,
                    io.micrometer.core.instrument.Clock.SYSTEM,
                    cloudWatchAsyncClient
            );

            new ClassLoaderMetrics().bindTo(cloudWatchRegistry);
            new JvmMemoryMetrics().bindTo(cloudWatchRegistry);
            new JvmGcMetrics().bindTo(cloudWatchRegistry);
            new ProcessorMetrics().bindTo(cloudWatchRegistry);
            new JvmThreadMetrics().bindTo(cloudWatchRegistry);

            new ClassLoaderMetrics().bindTo(prometheusRegistry);
            new JvmMemoryMetrics().bindTo(prometheusRegistry);
            new JvmGcMetrics().bindTo(prometheusRegistry);
            new ProcessorMetrics().bindTo(prometheusRegistry);
            new JvmThreadMetrics().bindTo(prometheusRegistry);

            cloudWatchRegistry.config().commonTags(
                    "application", "dlq-processor",
                    "environment", environment
            );

            prometheusRegistry.config().commonTags(
                    "application", "dlq-processor",
                    "environment", environment
            );

            log.info("CloudWatch and Prometheus metrics initialized for environment: {}", environment);
        } else {
            this.cloudWatchRegistry = null;
            log.info("Metrics are disabled");
        }
    }

    public void incrementMessagesReceived() {
        if (!enabled) return;
        getCounter("messages.received").increment();
    }

    public void incrementMessagesProcessed(String source, String status) {
        if (!enabled) return;
        getCounter("messages.processed", "source", source, "status", status).increment();
    }

    public void incrementEmailsSent(String source) {
        if (!enabled) return;
        getCounter("emails.sent", "source", source).increment();
    }

    public void incrementErrors(String type, String source) {
        if (!enabled) return;
        getCounter("errors.total", "type", type, "source", source).increment();
    }

    public void recordQueueSize(int size) {
        if (!enabled) return;
        cloudWatchRegistry.gauge("queue.size", size);
        prometheusRegistry.gauge("queue.size", size);
    }

    public Timer.Sample startTimer() {
        return enabled ? Timer.start(cloudWatchRegistry) : null;
    }

    public void stopTimer(Timer.Sample sample, String operation, String source) {
        if (enabled && sample != null) {
            Timer timer = getTimer("operation", operation, "source", source);
            sample.stop(timer);
        }
    }

    private Counter getCounter(String name, String... tags) {
        String key = name + ":" + String.join(",", tags);
        return counters.computeIfAbsent(key, k -> {
            Counter.Builder builder = Counter.builder(name);
            for (int i = 0; i < tags.length; i += 2) {
                if (i + 1 < tags.length) {
                    builder.tag(tags[i], tags[i + 1]);
                }
            }
            Counter counter = builder.register(cloudWatchRegistry);
            builder.register(prometheusRegistry);
            return counter;
        });
    }

    private Timer getTimer(String... tags) {
        String key = "processing.time" + ":" + String.join(",", tags);
        return timers.computeIfAbsent(key, k -> {
            Timer.Builder builder = Timer.builder("processing.time");
            for (int i = 0; i < tags.length; i += 2) {
                if (i + 1 < tags.length) {
                    builder.tag(tags[i], tags[i + 1]);
                }
            }
            Timer timer = builder.register(cloudWatchRegistry);
            builder.register(prometheusRegistry);
            return timer;
        });
    }

    public String getPrometheusMetrics() {
        return prometheusRegistry.scrape();
    }

    public void logStats() {
        if (!enabled) return;
        log.info("=== Metrics Stats ===");
        log.info("Messages processed: {}", getCounter("messages.processed").count());
        log.info("Emails sent: {}", getCounter("emails.sent").count());
        log.info("Errors: {}", getCounter("errors.total").count());
    }
}