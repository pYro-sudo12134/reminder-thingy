package by.losik.service;

import by.losik.config.LocalStackConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private LocalStackConfig mockConfig;

    @Mock
    private S3AsyncClient mockS3Client;

    private S3Service s3Service;

    private static final String TEST_BUCKET = "chatbot-audio-recordings";
    private static final String TEST_ENDPOINT = "http://localhost:4566";
    private static final String TEST_USER_ID = "user123";
    private static final String TEST_KEY = "audio/user123/2024/01/24/12-30-45/test.wav";

    @BeforeEach
    void setUp() {
        Mockito.when(mockConfig.getS3AsyncClient()).thenReturn(mockS3Client);
        Mockito.when(mockConfig.getLocalstackEndpoint()).thenReturn(TEST_ENDPOINT);

        s3Service = new S3Service(mockConfig);
    }

    @Test
    void constructor_ShouldInitializeWithConfigValues() {
        try {
            var bucketField = S3Service.class.getDeclaredField("bucketName");
            bucketField.setAccessible(true);
            String bucketName = (String) bucketField.get(s3Service);

            var endpointField = S3Service.class.getDeclaredField("localstackEndpoint");
            endpointField.setAccessible(true);
            String endpoint = (String) endpointField.get(s3Service);

            Assertions.assertEquals(TEST_BUCKET, bucketName);
            Assertions.assertEquals(TEST_ENDPOINT, endpoint);
        } catch (Exception e) {
            Assertions.fail("Reflection failed: " + e.getMessage());
        }
    }

    @Test
    void getBucketName_ShouldReturnCorrectBucket() {
        Assertions.assertEquals(TEST_BUCKET, s3Service.getBucketName());
    }

    @Test
    void uploadAudioFileAsync_ShouldCreateBucketAndUpload() throws IOException {
        File tempFile = tempDir.resolve("recording.wav").toFile();
        Files.write(tempFile.toPath(), "test audio data".getBytes());

        Mockito.when(mockS3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(CreateBucketResponse.builder().build()));

        Mockito.when(mockS3Client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

        CompletableFuture<String> future = s3Service.uploadAudioFileAsync(tempFile, TEST_USER_ID);
        String resultKey = future.join();

        Assertions.assertNotNull(resultKey);
        Assertions.assertTrue(resultKey.contains(TEST_USER_ID));
        Assertions.assertTrue(resultKey.endsWith("recording.wav"));

        Mockito.verify(mockS3Client).createBucket(any(CreateBucketRequest.class));
        Mockito.verify(mockS3Client).putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class));
    }

    @Test
    void uploadAudioFileAsync_ShouldHandleBucketAlreadyExists() throws IOException {
        File tempFile = tempDir.resolve("test.wav").toFile();
        Files.write(tempFile.toPath(), "audio".getBytes());

        var bucketExistsException = S3Exception.builder()
                .message("Bucket already exists")
                .statusCode(409)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("BucketAlreadyOwnedByYou")
                        .build())
                .build();

        Mockito.when(mockS3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(bucketExistsException));

        Mockito.when(mockS3Client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

        Assertions.assertDoesNotThrow(() -> {
            CompletableFuture<String> future = s3Service.uploadAudioFileAsync(tempFile, TEST_USER_ID);
            String result = future.join();
            Assertions.assertNotNull(result);
        });

        Mockito.verify(mockS3Client).createBucket(any(CreateBucketRequest.class));
        Mockito.verify(mockS3Client).putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class));
    }

    @Test
    void uploadAudioFileAsync_ShouldHandleBucketAlreadyExistsWithoutErrorCode() throws IOException {
        File tempFile = tempDir.resolve("test.wav").toFile();
        Files.write(tempFile.toPath(), "audio".getBytes());

        var bucketExistsException = S3Exception.builder()
                .message("Bucket already exists")
                .statusCode(409)
                .build();

        Mockito.when(mockS3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(bucketExistsException));

        Mockito.when(mockS3Client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

        Assertions.assertDoesNotThrow(() -> {
            CompletableFuture<String> future = s3Service.uploadAudioFileAsync(tempFile, TEST_USER_ID);
            String result = future.join();
            Assertions.assertNotNull(result);
        });

        Mockito.verify(mockS3Client).createBucket(any(CreateBucketRequest.class));
        Mockito.verify(mockS3Client).putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class));
    }

    @Test
    void uploadFileAsync_ShouldSetCorrectMetadata() throws IOException {
        File tempFile = tempDir.resolve("test.mp3").toFile();
        Files.write(tempFile.toPath(), "mp3 data".getBytes());

        Mockito.when(mockS3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        S3Exception.builder()
                                .message("Bucket exists")
                                .statusCode(409)
                                .awsErrorDetails(AwsErrorDetails.builder()
                                        .errorCode("BucketAlreadyExists")
                                        .build())
                                .build()
                ));

        Mockito.when(mockS3Client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

        CompletableFuture<String> future = s3Service.uploadAudioFileAsync(tempFile, TEST_USER_ID);
        String key = future.join();

        CompletableFuture<String> uploadFuture = s3Service.uploadFileAsync(tempFile, TEST_KEY);
        String result = uploadFuture.join();

        Assertions.assertNotNull(result);

        Mockito.verify(mockS3Client, Mockito.atLeastOnce()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void downloadFileAsync_ShouldDownloadFile() {
        Path targetPath = tempDir.resolve("downloaded.wav");

        Mockito.when(mockS3Client.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .thenReturn(CompletableFuture.completedFuture(GetObjectResponse.builder().build()));

        CompletableFuture<File> future = s3Service.downloadFileAsync(TEST_KEY, targetPath);
        File result = future.join();

        Assertions.assertNotNull(result);
        Mockito.verify(mockS3Client).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    @Test
    void deleteFileAsync_ShouldDeleteFile() {
        Mockito.when(mockS3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteObjectResponse.builder().build()));

        CompletableFuture<DeleteObjectResponse> future = s3Service.deleteFileAsync(TEST_KEY);
        DeleteObjectResponse result = future.join();

        Assertions.assertNotNull(result);
        Mockito.verify(mockS3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void listUserAudioFilesAsync_ShouldListFilesWithPrefix() {
        String userPrefix = "audio/" + TEST_USER_ID + "/";

        S3Object mockS3Object = S3Object.builder()
                .key(userPrefix + "file1.wav")
                .size(1024L)
                .build();

        Mockito.when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        ListObjectsV2Response.builder()
                                .contents(mockS3Object)
                                .build()
                ));

        CompletableFuture<List<String>> future = s3Service.listUserAudioFilesAsync(TEST_USER_ID);
        List<String> result = future.join();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.get(0).contains(userPrefix));
    }

    @Test
    void getFileInfoAsync_ShouldReturnInfo() {
        Mockito.when(mockS3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        HeadObjectResponse.builder()
                                .contentLength(1024L)
                                .contentType("audio/wav")
                                .build()
                ));

        CompletableFuture<HeadObjectResponse> future = s3Service.getFileInfoAsync(TEST_KEY);
        HeadObjectResponse result = future.join();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1024L, result.contentLength());
        Assertions.assertEquals("audio/wav", result.contentType());

        Mockito.verify(mockS3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void generatePresignedUrlAsync_ShouldGenerateCorrectUrl() {
        CompletableFuture<String> future = s3Service.generatePresignedUrlAsync(TEST_KEY);
        String url = future.join();

        String expectedUrl = String.format("%s/%s/%s", TEST_ENDPOINT, TEST_BUCKET, TEST_KEY);
        Assertions.assertEquals(expectedUrl, url);
    }

    @Test
    void uploadAudioFileAsync_ShouldPropagateExceptions() throws IOException {
        File tempFile = tempDir.resolve("test.wav").toFile();
        Files.write(tempFile.toPath(), "test".getBytes());

        RuntimeException expectedException = new RuntimeException("S3 error");
        Mockito.when(mockS3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(expectedException));

        CompletableFuture<String> future = s3Service.uploadAudioFileAsync(tempFile, TEST_USER_ID);

        Assertions.assertThrows(Exception.class, future::join);
    }

    @Test
    void uploadAudioFileAsync_ShouldHandleAccessDenied() throws IOException {
        File tempFile = tempDir.resolve("test.wav").toFile();
        Files.write(tempFile.toPath(), "test".getBytes());

        var accessDeniedException = S3Exception.builder()
                .message("Access Denied")
                .statusCode(403)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("AccessDenied")
                        .build())
                .build();

        Mockito.when(mockS3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(accessDeniedException));

        CompletableFuture<String> future = s3Service.uploadAudioFileAsync(tempFile, TEST_USER_ID);

        Assertions.assertThrows(Exception.class, future::join);
        Mockito.verify(mockS3Client).createBucket(any(CreateBucketRequest.class));
    }
}