package by.losik.service;

import by.losik.config.LocalStackConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Singleton
public class S3Service {

    private static final Logger log = LoggerFactory.getLogger(S3Service.class);
    private final S3AsyncClient s3AsyncClient;
    private final String bucketName;
    private final String localstackEndpoint;

    @Inject
    public S3Service(LocalStackConfig config) {
        this.s3AsyncClient = config.getS3AsyncClient();
        this.bucketName = "chatbot-audio-recordings";
        this.localstackEndpoint = config.getLocalstackEndpoint();
    }

    public CompletableFuture<String> uploadAudioFileAsync(File audioFile, String userId) {
        return ensureBucketExists()
                .thenCompose(v -> {
                    String key = generateAudioKey(userId, audioFile.getName());
                    return uploadFileAsync(audioFile, key);
                });
    }

    public CompletableFuture<String> uploadFileAsync(File file, String key) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(getContentType(file.getName()))
                .metadata(Map.of("original-filename", file.getName(),
                                "upload-timestamp", LocalDateTime.now().toString(),
                                "file-size", String.valueOf(file.length())
                        ))
                .build();

        return s3AsyncClient.putObject(request, AsyncRequestBody.fromFile(file.toPath()))
                .thenApply(response -> {
                    log.info("File uploaded successfully: {}", key);
                    return key;
                })
                .exceptionally(ex -> {
                    log.error("Failed to upload file: {}", key, ex);
                    throw new RuntimeException("Failed to upload file: " + key, ex);
                });
    }

    public CompletableFuture<File> downloadFileAsync(String key, Path targetPath) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3AsyncClient.getObject(request, AsyncResponseTransformer.toFile(targetPath))
                .thenApply(response -> {
                    log.info("File downloaded successfully: {}", key);
                    return targetPath.toFile();
                })
                .exceptionally(ex -> {
                    log.error("Failed to download file: {}", key, ex);
                    throw new RuntimeException("Failed to download file: " + key, ex);
                });
    }

    public CompletableFuture<DeleteObjectResponse> deleteFileAsync(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3AsyncClient.deleteObject(request)
                .thenApply(response -> {
                    log.info("File deleted successfully: {}", key);
                    return response;
                })
                .exceptionally(ex -> {
                    log.error("Failed to delete file: {}", key, ex);
                    throw new RuntimeException("Failed to delete file: " + key, ex);
                });
    }

    public CompletableFuture<List<String>> listUserAudioFilesAsync(String userId) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix("audio/" + userId + "/")
                .build();

        return s3AsyncClient.listObjectsV2(request)
                .thenApply(response -> {
                    List<String> keys = response.contents().stream()
                            .map(S3Object::key)
                            .collect(Collectors.toList());
                    log.info("Found {} audio files for user {}", keys.size(), userId);
                    return keys;
                })
                .exceptionally(ex -> {
                    log.error("Failed to list audio files for user: {}", userId, ex);
                    throw new RuntimeException("Failed to list audio files", ex);
                });
    }

    public CompletableFuture<HeadObjectResponse> getFileInfoAsync(String key) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3AsyncClient.headObject(request)
                .exceptionally(ex -> {
                    log.error("Failed to get file info: {}", key, ex);
                    throw new RuntimeException("Failed to get file info: " + key, ex);
                });
    }

    public CompletableFuture<String> generatePresignedUrlAsync(String key) {
        return CompletableFuture.completedFuture(
                String.format("%s/%s/%s",
                        localstackEndpoint,
                        bucketName,
                        key
                )
        );
    }

    private CompletableFuture<CreateBucketResponse> ensureBucketExists() {
        CreateBucketRequest request = CreateBucketRequest.builder()
                .bucket(bucketName)
                .build();

        return s3AsyncClient.createBucket(request)
                .thenApply(response -> {
                    log.info("Bucket created: {}", bucketName);
                    return response;
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause();
                    if (cause instanceof S3Exception s3Exception) {
                        String errorCode = s3Exception.awsErrorDetails().errorCode();
                        if (errorCode.equals("BucketAlreadyExists") ||
                                errorCode.equals("BucketAlreadyOwnedByYou")) {
                            log.info("Bucket already exists: {}", bucketName);
                            return null;
                        }
                    }

                    log.warn("Bucket creation check failed (might already exist): {}", ex.getMessage());
                    return null;
                });
    }

    private String generateAudioKey(String userId, String originalFileName) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd/HH-mm-ss"));

        String safeFileName = originalFileName.replaceAll("[^a-zA-Z\\d.-]", "_");

        return String.format("audio/%s/%s/%s",
                userId, timestamp, safeFileName);
    }

    private String getContentType(String fileName) {
        if (fileName.endsWith(".wav")) return "audio/wav";
        if (fileName.endsWith(".mp3")) return "audio/mpeg";
        if (fileName.endsWith(".ogg")) return "audio/ogg";
        return "application/octet-stream";
    }

    public String getBucketName() {
        return bucketName;
    }
}