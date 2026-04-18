package by.losik.resource;

import by.losik.config.MonitoringConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * REST ресурс для получения метрик Prometheus.
 * <p>
 * Предоставляет endpoint для scraping метрик системой мониторинга.
 */
@Path("/metrics")
@Singleton
public class MetricsResource {
    private static final Logger log = LoggerFactory.getLogger(MetricsResource.class);

    /** Таймаут на scraping метрик по умолчанию (10 секунд) */
    private static final long METRICS_SCRAPING_TIMEOUT_SEC = 10L;

    private final PrometheusMeterRegistry prometheusRegistry;

    /**
     * Создаёт ресурс метрик с внедрённым реестром Prometheus.
     *
     * @param monitoringConfig конфигурация мониторинга
     */
    @Inject
    public MetricsResource(MonitoringConfig monitoringConfig) {
        this.prometheusRegistry = monitoringConfig.getPrometheusRegistry();
    }

    /**
     * Получает метрики Prometheus в формате text/plain.
     *
     * @param asyncResponse асинхронный ответ
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public void getPrometheusMetrics(@Suspended AsyncResponse asyncResponse) {
        CompletableFuture.supplyAsync(() -> {
            try {
                String metrics = prometheusRegistry.scrape();
                return Response.ok(metrics).build();
            } catch (Exception e) {
                log.error("Failed to scrape metrics", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Error scraping metrics: " + e.getMessage())
                        .build();
            }
        }).thenAccept(asyncResponse::resume);

        asyncResponse.setTimeout(METRICS_SCRAPING_TIMEOUT_SEC, TimeUnit.SECONDS);
        asyncResponse.setTimeoutHandler(ar ->
                ar.resume(Response.status(Response.Status.REQUEST_TIMEOUT)
                        .entity("Metrics scraping timeout")
                        .build()));
    }
}