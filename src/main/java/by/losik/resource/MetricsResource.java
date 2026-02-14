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

@Path("/metrics")
@Singleton
public class MetricsResource {
    private static final Logger log = LoggerFactory.getLogger(MetricsResource.class);

    private final PrometheusMeterRegistry prometheusRegistry;

    @Inject
    public MetricsResource(MonitoringConfig monitoringConfig) {
        this.prometheusRegistry = monitoringConfig.getPrometheusRegistry();
    }

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

        asyncResponse.setTimeout(10, TimeUnit.SECONDS);
        asyncResponse.setTimeoutHandler(ar ->
                ar.resume(Response.status(Response.Status.REQUEST_TIMEOUT)
                        .entity("Metrics scraping timeout")
                        .build()));
    }
}