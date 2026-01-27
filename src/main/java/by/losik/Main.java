package by.losik;

import by.losik.composition.root.CompositionRoot;
import by.losik.service.OpenSearchService;
import by.losik.server.WebServer;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) {
        try {
            Injector injector = Guice.createInjector(new CompositionRoot());
            OpenSearchService openSearchService = injector.getInstance(OpenSearchService.class);
            openSearchService.initializeIndices().join();
            log.info("OpenSearch indices initialized");
            WebServer webServer = injector.getInstance(WebServer.class);
            webServer.start();
            log.info("Web application started!");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    log.info("\nShutting down");
                    webServer.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));

            Thread.currentThread().join();

        } catch (Exception e) {
            log.error("Error starting application: {}", e.getMessage());
            e.printStackTrace();
        }
    }
}