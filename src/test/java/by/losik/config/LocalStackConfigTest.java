package by.losik.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;

import java.lang.reflect.Field;

@ExtendWith(MockitoExtension.class)
class LocalStackConfigTest {

    private LocalStackConfig config;
    private final String endpoint = "http://localhost:4566";
    private final String region = "us-east-1";
    private final String accessKey = "test-access-key";
    private final String secretKey = "test-secret-key";
    private final String email = "test@example.com";

    @BeforeEach
    void setUp() {
        config = new LocalStackConfig(endpoint, region, accessKey, secretKey, email);
    }

    @Test
    void constructor_shouldInitializeAllFields() {
        Assertions.assertNotNull(config.getEventBridgeAsyncClient());
        Assertions.assertNotNull(config.getS3AsyncClient());
        Assertions.assertNotNull(config.getTranscribeAsyncClient());
        Assertions.assertNotNull(config.getLambdaAsyncClient());
        Assertions.assertNotNull(config.getOpenSearchClient());
        Assertions.assertNotNull(config.getOpenSearchAsyncClient());
        Assertions.assertNotNull(config.getAsyncHttpClient());
        Assertions.assertNotNull(config.getSesAsyncClient());
    }

    @Test
    void getLocalstackEndpoint_shouldReturnCorrectEndpoint() {
        Assertions.assertEquals(endpoint, config.getLocalstackEndpoint());
    }

    @Test
    void getRegion_shouldReturnCorrectRegion() {
        Assertions.assertEquals(region, config.getRegion());
    }

    @Test
    void getEmail_shouldReturnCorrectEmail() {
        Assertions.assertEquals(email, config.getEmail());
    }

    @Test
    void getS3Endpoint_shouldReturnCorrectEndpoint() {
        Assertions.assertEquals(endpoint, config.getS3Endpoint());
    }

    @Test
    void getTranscribeEndpoint_shouldReturnCorrectEndpoint() {
        Assertions.assertEquals(endpoint, config.getTranscribeEndpoint());
    }

    @Test
    void getOpenSearchEndpoint_shouldReplacePort() {
        String expectedEndpoint = endpoint.replace("4566", "4571");
        Assertions.assertEquals(expectedEndpoint, config.getOpenSearchEndpoint());
    }

    @Test
    void createAsyncHttpClient_shouldConfigureCorrectly() {
        try {
            Field asyncHttpClientField = LocalStackConfig.class.getDeclaredField("asyncHttpClient");
            asyncHttpClientField.setAccessible(true);
            var httpClient = asyncHttpClientField.get(config);
            Assertions.assertNotNull(httpClient);
        } catch (Exception e) {
            Assertions.fail("Failed to access asyncHttpClient field: " + e.getMessage());
        }
    }

    @Test
    void createCredentialsProvider_shouldUseProvidedCredentials() {
        var eventBridgeClient = config.getEventBridgeAsyncClient();
        Assertions.assertNotNull(eventBridgeClient);
        var clientRegion = eventBridgeClient.serviceClientConfiguration().region();
        Assertions.assertEquals(Region.of(region), clientRegion);
    }

    @Test
    void shutdown_shouldCloseAllClients() {
        Assertions.assertDoesNotThrow(() -> config.shutdown());
        Assertions.assertDoesNotThrow(() -> config.shutdown());
    }

    @Test
    void testToString_notImplemented_shouldNotThrow() {
        Assertions.assertDoesNotThrow(() -> config.toString());
    }
}