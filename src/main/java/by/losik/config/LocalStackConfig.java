package by.losik.config;

import com.google.inject.Singleton;
import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.iam.IamAsyncClient;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.opensearch.OpenSearchAsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.ses.SesAsyncClient;
import software.amazon.awssdk.services.transcribe.TranscribeAsyncClient;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

@Singleton
public class LocalStackConfig {
    private final String LOCALSTACK_ENDPOINT;
    private final String REGION;
    private final String ACCESS_KEY;
    private final String SECRET_KEY;
    private final String EMAIL;
    private final EventBridgeAsyncClient eventBridgeAsyncClient;
    private final S3AsyncClient s3AsyncClient;
    private final TranscribeAsyncClient transcribeAsyncClient;
    private final LambdaAsyncClient lambdaAsyncClient;
    private final RestHighLevelClient openSearchClient;
    private final OpenSearchAsyncClient openSearchAsyncClient;
    private final SdkAsyncHttpClient asyncHttpClient;
    private final SesAsyncClient sesAsyncClient;

    public LocalStackConfig(String localstackEndpoint, String region, String accessKey, String secretKey, String email) {
        this.LOCALSTACK_ENDPOINT = localstackEndpoint;
        this.REGION = region;
        this.ACCESS_KEY = accessKey;
        this.SECRET_KEY = secretKey;
        this.EMAIL = email;
        this.asyncHttpClient = createAsyncHttpClient();
        this.eventBridgeAsyncClient = createEventBridgeAsyncClient();
        this.s3AsyncClient = createS3AsyncClient();
        this.transcribeAsyncClient = createTranscribeAsyncClient();
        this.lambdaAsyncClient = createLambdaAsyncClient();
        this.openSearchClient = createOpenSearchClient();
        this.openSearchAsyncClient = createOpenSearchAsyncClient();
        this.sesAsyncClient = createSesAsyncClient();
    }

    public EventBridgeAsyncClient getEventBridgeAsyncClient() {
        return eventBridgeAsyncClient;
    }

    public S3AsyncClient getS3AsyncClient() {
        return s3AsyncClient;
    }

    public TranscribeAsyncClient getTranscribeAsyncClient() {
        return transcribeAsyncClient;
    }

    public LambdaAsyncClient getLambdaAsyncClient() {
        return lambdaAsyncClient;
    }
    public RestHighLevelClient getOpenSearchClient() {
        return openSearchClient;
    }

    public OpenSearchAsyncClient getOpenSearchAsyncClient() {
        return openSearchAsyncClient;
    }

    public SdkAsyncHttpClient getAsyncHttpClient() {
        return asyncHttpClient;
    }

    public String getLocalstackEndpoint() {
        return LOCALSTACK_ENDPOINT;
    }

    public String getRegion() {
        return REGION;
    }
    public String getEmail() {
        return EMAIL;
    }

    private SdkAsyncHttpClient createAsyncHttpClient() {
        return NettyNioAsyncHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(10))
                .connectionMaxIdleTime(Duration.ofSeconds(5))
                .maxConcurrency(100)
                .build();
    }

    private EventBridgeAsyncClient createEventBridgeAsyncClient() {
        return EventBridgeAsyncClient.builder()
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .region(Region.of(REGION))
                .credentialsProvider(createCredentialsProvider())
                .httpClient(asyncHttpClient)
                .build();
    }

    private S3AsyncClient createS3AsyncClient() {
        return S3AsyncClient.builder()
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .region(Region.of(REGION))
                .credentialsProvider(createCredentialsProvider())
                .serviceConfiguration(s3 -> s3
                        .pathStyleAccessEnabled(true)
                )
                .httpClient(asyncHttpClient)
                .build();
    }

    private SesAsyncClient createSesAsyncClient() {
        return SesAsyncClient.builder()
                .endpointOverride(java.net.URI.create(LOCALSTACK_ENDPOINT))
                .region(software.amazon.awssdk.regions.Region.of(REGION))
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                                ACCESS_KEY, SECRET_KEY
                        )
                ))
                .build();
    }

    private TranscribeAsyncClient createTranscribeAsyncClient() {
        return TranscribeAsyncClient.builder()
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .region(Region.of(REGION))
                .credentialsProvider(createCredentialsProvider())
                .httpClient(asyncHttpClient)
                .build();
    }

    private LambdaAsyncClient createLambdaAsyncClient() {
        return LambdaAsyncClient.builder()
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .region(Region.of(REGION))
                .credentialsProvider(createCredentialsProvider())
                .httpClient(asyncHttpClient)
                .build();
    }

    private IamAsyncClient createIamAsyncClient() {
        return IamAsyncClient.builder()
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .region(Region.of(REGION))
                .credentialsProvider(createCredentialsProvider())
                .httpClient(asyncHttpClient)
                .build();
    }

    private RestHighLevelClient createOpenSearchClient() {
        RestClientBuilder builder = RestClient.builder(
                HttpHost.create(LOCALSTACK_ENDPOINT.replace("4566", "9200"))
        );

        builder.setHttpClientConfigCallback(httpClientBuilder ->
                httpClientBuilder);

        return new RestHighLevelClient(builder);
    }

    private OpenSearchAsyncClient createOpenSearchAsyncClient() {
        return OpenSearchAsyncClient.builder()
                .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                .region(Region.of(REGION))
                .credentialsProvider(createCredentialsProvider())
                .httpClient(asyncHttpClient)
                .build();
    }

    private StaticCredentialsProvider createCredentialsProvider() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)
        );
    }


    public String getS3Endpoint() {
        return LOCALSTACK_ENDPOINT;
    }

    public String getTranscribeEndpoint() {
        return LOCALSTACK_ENDPOINT;
    }

    public String getOpenSearchEndpoint() {
        return LOCALSTACK_ENDPOINT.replace("4566", "4571");
    }

    public void shutdown() {
        if (eventBridgeAsyncClient != null) {
            eventBridgeAsyncClient.close();
        }
        if (s3AsyncClient != null) {
            s3AsyncClient.close();
        }
        if (transcribeAsyncClient != null) {
            transcribeAsyncClient.close();
        }
        if (lambdaAsyncClient != null) {
            lambdaAsyncClient.close();
        }
        if (openSearchClient != null) {
            try {
                openSearchClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (openSearchAsyncClient != null) {
            openSearchAsyncClient.close();
        }
    }

    public SesAsyncClient getSesAsyncClient() {
        return sesAsyncClient;
    }
}