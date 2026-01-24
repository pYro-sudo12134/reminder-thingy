package by.losik.service;

import by.losik.config.LocalStackConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.Runtime;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class LambdaHandlerTest {

    @Mock
    private LocalStackConfig config;

    @Mock
    private LambdaAsyncClient lambdaAsyncClient;

    private LambdaHandler lambdaHandler;

    @BeforeEach
    void setUp() {
        Mockito.when(config.getLambdaAsyncClient()).thenReturn(lambdaAsyncClient);
        lambdaHandler = new LambdaHandler(config);
    }

    @Test
    void constructor_ShouldInitializeLambdaClient() {
        Mockito.when(config.getLambdaAsyncClient()).thenReturn(lambdaAsyncClient);

        LambdaHandler handler = new LambdaHandler(config);

        Mockito.verify(config, Mockito.atLeast(1)).getLambdaAsyncClient();
        Assertions.assertNotNull(handler);
    }

    @Test
    void deployLambdaFunction_ShouldCreateFunctionWithCorrectParameters() {
        byte[] mockCode = "mock lambda code".getBytes();
        CreateFunctionResponse response = CreateFunctionResponse.builder()
                .functionArn("arn:aws:lambda:us-east-1:123456789012:function:send-reminder")
                .build();

        Mockito.when(lambdaAsyncClient.createFunction(any(CreateFunctionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        try (var filesMock = Mockito.mockStatic(Files.class);
             var pathsMock = Mockito.mockStatic(Paths.class)) {

            pathsMock.when(() -> Paths.get("build/libs/send-reminder-lambda.jar"))
                    .thenReturn(java.nio.file.Path.of("mock-path"));
            filesMock.when(() -> Files.readAllBytes(any(java.nio.file.Path.class)))
                    .thenReturn(mockCode);

            CompletableFuture<String> resultFuture = lambdaHandler.deployLambdaFunction();
            String functionArn = resultFuture.join();

            Assertions.assertEquals("arn:aws:lambda:us-east-1:123456789012:function:send-reminder", functionArn);

            ArgumentCaptor<CreateFunctionRequest> requestCaptor =
                    ArgumentCaptor.forClass(CreateFunctionRequest.class);
            Mockito.verify(lambdaAsyncClient).createFunction(requestCaptor.capture());

            CreateFunctionRequest capturedRequest = requestCaptor.getValue();
            Assertions.assertEquals("send-reminder", capturedRequest.functionName());
            Assertions.assertEquals(Runtime.JAVA17, capturedRequest.runtime());
            Assertions.assertEquals("arn:aws:iam::000000000000:role/lambda-role", capturedRequest.role());
            Assertions.assertEquals("by.losik.lambda.SendReminderLambda::handleRequest", capturedRequest.handler());
            Assertions.assertEquals(30, capturedRequest.timeout());
            Assertions.assertEquals(512, capturedRequest.memorySize());

            SdkBytes codeBytes = capturedRequest.code().zipFile();
            Assertions.assertNotNull(codeBytes);
            Assertions.assertArrayEquals(mockCode, codeBytes.asByteArray());
        }
    }

    @Test
    void deployLambdaFunction_WhenFileReadFails_ShouldCompleteExceptionally() {
        try (var filesMock = Mockito.mockStatic(Files.class);
             var pathsMock = Mockito.mockStatic(Paths.class)) {

            pathsMock.when(() -> Paths.get("build/libs/send-reminder-lambda.jar"))
                    .thenReturn(java.nio.file.Path.of("mock-path"));
            filesMock.when(() -> Files.readAllBytes(any(java.nio.file.Path.class)))
                    .thenThrow(new RuntimeException("File not found"));

            CompletableFuture<String> resultFuture = lambdaHandler.deployLambdaFunction();

            Assertions.assertTrue(resultFuture.isCompletedExceptionally());

            RuntimeException exception = Assertions.assertThrows(RuntimeException.class,
                    resultFuture::join);
            Assertions.assertTrue(exception.getCause() instanceof RuntimeException);
            Assertions.assertEquals("File not found", exception.getCause().getMessage());

            Mockito.verify(lambdaAsyncClient, Mockito.never()).createFunction((CreateFunctionRequest) any());
        }
    }

    @Test
    void deployLambdaFunction_WhenCreateFunctionFails_ShouldReturnNull() {
        byte[] mockCode = "mock code".getBytes();

        RuntimeException expectedException = new RuntimeException("Lambda creation failed");
        Mockito.when(lambdaAsyncClient.createFunction(any(CreateFunctionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(expectedException));

        try (var filesMock = Mockito.mockStatic(Files.class);
             var pathsMock = Mockito.mockStatic(Paths.class)) {

            pathsMock.when(() -> Paths.get("build/libs/send-reminder-lambda.jar"))
                    .thenReturn(java.nio.file.Path.of("mock-path"));
            filesMock.when(() -> Files.readAllBytes(any(java.nio.file.Path.class)))
                    .thenReturn(mockCode);

            CompletableFuture<String> resultFuture = lambdaHandler.deployLambdaFunction();
            String result = resultFuture.join();

            Assertions.assertNull(result);
            Mockito.verify(lambdaAsyncClient).createFunction((CreateFunctionRequest) any());
        }
    }

    @Test
    void deployLambdaFunction_WithDifferentParameters_ShouldConfigureCorrectly() {
        byte[] mockCode = "different code".getBytes();
        CreateFunctionResponse response = CreateFunctionResponse.builder()
                .functionArn("arn:aws:lambda:eu-west-1:function:different-name")
                .build();

        Mockito.when(lambdaAsyncClient.createFunction(any(CreateFunctionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        try (var filesMock = Mockito.mockStatic(Files.class);
             var pathsMock = Mockito.mockStatic(Paths.class)) {

            pathsMock.when(() -> Paths.get("build/libs/send-reminder-lambda.jar"))
                    .thenReturn(java.nio.file.Path.of("different-path"));
            filesMock.when(() -> Files.readAllBytes(any(java.nio.file.Path.class)))
                    .thenReturn(mockCode);

            CompletableFuture<String> resultFuture = lambdaHandler.deployLambdaFunction();
            String functionArn = resultFuture.join();

            Assertions.assertEquals("arn:aws:lambda:eu-west-1:function:different-name", functionArn);

            ArgumentCaptor<CreateFunctionRequest> requestCaptor =
                    ArgumentCaptor.forClass(CreateFunctionRequest.class);
            Mockito.verify(lambdaAsyncClient).createFunction(requestCaptor.capture());

            CreateFunctionRequest capturedRequest = requestCaptor.getValue();
            Assertions.assertNotNull(capturedRequest);
            Assertions.assertEquals("send-reminder", capturedRequest.functionName()); // Имя фиксировано в коде
        }
    }

    @Test
    void deployLambdaFunction_WhenLambdaClientReturnsNull_ShouldHandleGracefully() {
        byte[] mockCode = "code".getBytes();

        Mockito.when(lambdaAsyncClient.createFunction(any(CreateFunctionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        try (var filesMock = Mockito.mockStatic(Files.class);
             var pathsMock = Mockito.mockStatic(Paths.class)) {

            pathsMock.when(() -> Paths.get("build/libs/send-reminder-lambda.jar"))
                    .thenReturn(java.nio.file.Path.of("path"));
            filesMock.when(() -> Files.readAllBytes(any(java.nio.file.Path.class)))
                    .thenReturn(mockCode);

            CompletableFuture<String> resultFuture = lambdaHandler.deployLambdaFunction();
            String result = resultFuture.join();

            Assertions.assertNull(result);
            Mockito.verify(lambdaAsyncClient).createFunction((CreateFunctionRequest) any());
        }
    }

    @Test
    void deployLambdaFunction_WithNetworkError_ShouldReturnNull() {
        byte[] mockCode = "code".getBytes();

        Mockito.when(lambdaAsyncClient.createFunction(any(CreateFunctionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Network timeout")));

        try (var filesMock = Mockito.mockStatic(Files.class);
             var pathsMock = Mockito.mockStatic(Paths.class)) {

            pathsMock.when(() -> Paths.get("build/libs/send-reminder-lambda.jar"))
                    .thenReturn(java.nio.file.Path.of("path"));
            filesMock.when(() -> Files.readAllBytes(any(java.nio.file.Path.class)))
                    .thenReturn(mockCode);

            CompletableFuture<String> resultFuture = lambdaHandler.deployLambdaFunction();
            String result = resultFuture.join();

            Assertions.assertNull(result);
            Mockito.verify(lambdaAsyncClient).createFunction((CreateFunctionRequest) any());
        }
    }
}