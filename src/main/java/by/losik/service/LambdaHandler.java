package by.losik.service;

import by.losik.config.LocalStackConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.Runtime;

import java.io.ByteArrayOutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Singleton
public class LambdaHandler {
    private final LambdaAsyncClient lambdaClient;
    private static final String LAMBDA_DIR = "src/main/python/lambda/SendReminderEmail";

    @Inject
    public LambdaHandler(LocalStackConfig config) {
        this.lambdaClient = config.getLambdaAsyncClient();
    }

    public CompletableFuture<String> deployLambdaFunction() {
        try {
            byte[] lambdaZip = createLambdaZip();

            CreateFunctionRequest request = CreateFunctionRequest.builder()
                    .functionName("send-reminder-email")
                    .runtime(Runtime.PYTHON3_11)
                    .role("arn:aws:iam::000000000000:role/lambda-role")
                    .handler("SendReminderLambda.lambda_handler")
                    .code(FunctionCode.builder()
                            .zipFile(SdkBytes.fromByteArray(lambdaZip))
                            .build())
                    .timeout(30)
                    .memorySize(512)
                    .build();

            return lambdaClient.createFunction(request)
                    .thenApply(CreateFunctionResponse::functionArn)
                    .exceptionally(ex -> {
                        System.err.println("Failed to deploy lambda: " + ex.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    private byte[] createLambdaZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Path lambdaDir = Paths.get(LAMBDA_DIR);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(lambdaDir, "*.py")) {
                for (Path file : stream) {
                    String fileName = file.getFileName().toString();
                    ZipEntry entry = new ZipEntry(fileName);
                    zos.putNextEntry(entry);
                    zos.write(Files.readAllBytes(file));
                    zos.closeEntry();
                }
            }
        }
        return baos.toByteArray();
    }
}
