package by.losik.service;

import by.losik.config.LocalStackConfig;
import by.losik.config.S3Config;
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

/**
 * Сервис для работы с AWS S3 (объектное хранилище).
 * <p>
 * Предоставляет методы для:
 * <ul>
 *     <li>Загрузки аудиофайлов пользователей</li>
 *     <li>Скачивания файлов из S3</li>
 *     <li>Удаления файлов</li>
 *     <li>Просмотра списка файлов пользователя</li>
 *     <li>Генерации presigned URL</li>
 * </ul>
 * <p>
 * Использует асинхронный S3AsyncClient для всех операций.
 *
 * @see S3Config
 */
@Singleton
public class S3Service {

    private static final Logger log = LoggerFactory.getLogger(S3Service.class);
    private final S3AsyncClient s3AsyncClient;
    private final S3Config s3Config;
    private final String localstackEndpoint;

    /**
     * Создаёт S3 сервис с конфигурацией.
     *
     * @param localStackConfig конфигурация LocalStack для клиента
     * @param s3Config конфигурация S3 (имя бакета, настройки доступа)
     */
    @Inject
    public S3Service(LocalStackConfig localStackConfig, S3Config s3Config) {
        this.s3AsyncClient = localStackConfig.getS3AsyncClient();
        this.s3Config = s3Config;
        this.localstackEndpoint = localStackConfig.getLocalstackEndpoint();
    }

    /**
     * Загружает аудиофайл пользователя в S3.
     *
     * @param audioFile аудиофайл для загрузки
     * @param userId ID пользователя
     * @return ключ загруженного файла в S3
     */
    public CompletableFuture<String> uploadAudioFileAsync(File audioFile, String userId) {
        return ensureBucketExists()
                .thenCompose(response -> {
                    String key = generateAudioKey(userId, audioFile.getName());
                    return uploadFileAsync(audioFile, key);
                });
    }

    /**
     * Загружает файл в S3 по указанному ключу.
     *
     * @param file файл для загрузки
     * @param key ключ файла в S3
     * @return ключ загруженного файла
     */
    public CompletableFuture<String> uploadFileAsync(File file, String key) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Config.getBucketName())
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

    /**
     * Скачивает файл из S3 по указанному ключу.
     *
     * @param key ключ файла в S3
     * @param targetPath путь для сохранения файла
     * @return скачанный файл
     */
    public CompletableFuture<File> downloadFileAsync(String key, Path targetPath) {
        return downloadFileAsync(key, targetPath, s3Config.getBucketName());
    }

    /**
     * Скачивает файл из S3 по указанному ключу из указанного бакета.
     *
     * @param key ключ файла в S3
     * @param targetPath путь для сохранения файла
     * @param bucketName имя бакета
     * @return скачанный файл
     */
    public CompletableFuture<File> downloadFileAsync(String key, Path targetPath, String bucketName) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3AsyncClient.getObject(request, AsyncResponseTransformer.toFile(targetPath))
                .thenApply(response -> {
                    log.info("File downloaded successfully: {} from bucket {}", key, bucketName);
                    return targetPath.toFile();
                })
                .exceptionally(ex -> {
                    log.error("Failed to download file: {} from bucket {}", key, bucketName, ex);
                    throw new RuntimeException("Failed to download file: " + key + " from bucket: " + bucketName, ex);
                });
    }

    /**
     * Удаляет файл из S3 по указанному ключу.
     *
     * @param key ключ файла в S3
     * @return результат удаления
     */
    public CompletableFuture<DeleteObjectResponse> deleteFileAsync(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(s3Config.getBucketName())
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

    /**
     * Получает список аудиофайлов пользователя.
     *
     * @param userId ID пользователя
     * @return список ключей файлов
     */
    public CompletableFuture<List<String>> listUserAudioFilesAsync(String userId) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(s3Config.getBucketName())
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

    /**
     * Получает информацию о файле (метаданные).
     *
     * @param key ключ файла в S3
     * @return метаданные файла
     */
    public CompletableFuture<HeadObjectResponse> getFileInfoAsync(String key) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(s3Config.getBucketName())
                .key(key)
                .build();

        return s3AsyncClient.headObject(request)
                .exceptionally(ex -> {
                    log.error("Failed to get file info: {}", key, ex);
                    throw new RuntimeException("Failed to get file info: " + key, ex);
                });
    }

    /**
     * Генерирует presigned URL для доступа к файлу.
     *
     * @param key ключ файла в S3
     * @return URL для доступа к файлу
     */
    public CompletableFuture<String> generatePresignedUrlAsync(String key) {
        return CompletableFuture.completedFuture(
                String.format("%s/%s/%s",
                        localstackEndpoint,
                        s3Config.getBucketName(),
                        key
                )
        );
    }

    /**
     * Проверяет существование бакета и создаёт его при необходимости.
     *
     * @return CompletableFuture с результатом создания бакета
     */
    private CompletableFuture<CreateBucketResponse> ensureBucketExists() {
        CreateBucketRequest request = CreateBucketRequest.builder()
                .bucket(s3Config.getBucketName())
                .build();

        return s3AsyncClient.createBucket(request)
                .thenApply(response -> {
                    log.info("Bucket created: {}", s3Config.getBucketName());
                    return response;
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause();
                    if (cause instanceof S3Exception s3Exception) {
                        String errorCode = s3Exception.awsErrorDetails() != null ?
                                s3Exception.awsErrorDetails().errorCode() : null;

                        if (errorCode != null && (
                                errorCode.equals("BucketAlreadyExists") ||
                                        errorCode.equals("BucketAlreadyOwnedByYou"))) {
                            log.info("Bucket already exists: {}", s3Config.getBucketName());
                        } else {
                            log.warn("Bucket creation failed with error code {}: {}",
                                    errorCode, ex.getMessage());
                        }
                    } else {
                        log.warn("Bucket creation failed: {}", ex.getMessage());
                    }

                    return null;
                });
    }

    /**
     * Генерирует ключ S3 для аудиофайла пользователя.
     *
     * @param userId ID пользователя
     * @param originalFileName оригинальное имя файла
     * @return ключ в формате "audio/{userId}/{timestamp}/{fileName}"
     */
    private String generateAudioKey(String userId, String originalFileName) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd/HH-mm-ss"));

        String safeFileName = originalFileName.replaceAll("[^a-zA-Z\\d.-]", "_");

        return String.format("audio/%s/%s/%s",
                userId, timestamp, safeFileName);
    }

    /**
     * Определяет Content-Type по расширению файла.
     *
     * @param fileName имя файла
     * @return Content-Type (audio/wav, audio/mpeg, audio/ogg, или application/octet-stream)
     */
    private String getContentType(String fileName) {
        if (fileName.endsWith(".wav")) return "audio/wav";
        if (fileName.endsWith(".mp3")) return "audio/mpeg";
        if (fileName.endsWith(".ogg")) return "audio/ogg";
        return "application/octet-stream";
    }

    /**
     * Получает имя бакета S3.
     *
     * @return имя бакета
     */
    public String getBucketName() {
        return s3Config.getBucketName();
    }
}