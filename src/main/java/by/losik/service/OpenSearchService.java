package by.losik.service;

import by.losik.config.LocalStackConfig;
import by.losik.dto.ReminderRecord;
import by.losik.dto.TranscriptionResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Singleton
public class OpenSearchService {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchService.class);
    private final RestHighLevelClient openSearchClient;

    private static final String TRANSCRIPTION_INDEX = "transcriptions";
    private static final String REMINDER_INDEX = "reminders";

    @Inject
    public OpenSearchService(LocalStackConfig config) {
        this.openSearchClient = config.getOpenSearchClient();
    }

    public CompletableFuture<Void> initializeIndices() {
        return CompletableFuture.runAsync(() -> {
            try {
                ensureTranscriptionIndex();
                ensureReminderIndex();
                log.info("OpenSearch indices initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize OpenSearch indices", e);
                throw new RuntimeException("Failed to initialize indices", e);
            }
        });
    }

    private void ensureTranscriptionIndex() throws IOException {
        if (!indexExists(TRANSCRIPTION_INDEX)) {
            CreateIndexRequest request = new CreateIndexRequest(TRANSCRIPTION_INDEX);

            XContentBuilder mapping = XContentFactory.jsonBuilder()
                    .startObject()
                    .startObject("properties")
                    .startObject("original_audio_key")
                    .field("type", "keyword")
                    .endObject()
                    .startObject("transcribed_text")
                    .field("type", "text")
                    .field("analyzer", "russian")
                    .endObject()
                    .startObject("confidence")
                    .field("type", "float")
                    .endObject()
                    .startObject("language")
                    .field("type", "keyword")
                    .endObject()
                    .startObject("duration_seconds")
                    .field("type", "float")
                    .endObject()
                    .startObject("user_id")
                    .field("type", "keyword")
                    .endObject()
                    .startObject("completed_at")
                    .field("type", "date")
                    .field("format", "strict_date_optional_time||epoch_millis")
                    .endObject()
                    .startObject("indexed_at")
                    .field("type", "date")
                    .field("format", "strict_date_optional_time||epoch_millis")
                    .endObject()
                    .endObject()
                    .endObject();

            request.mapping(mapping);
            var response = openSearchClient.indices().create(request, RequestOptions.DEFAULT);
            log.info("Created transcription index: {}", response.index());
        }
    }

    private XContentBuilder createCustomAnalyzer() throws IOException {
        return XContentFactory.jsonBuilder()
                .startObject()
                .startObject("settings")
                .startObject("analysis")
                .startObject("analyzer")
                .startObject("russian_with_entities")
                .field("type", "custom")
                .field("tokenizer", "standard")
                .field("filter", new String[]{
                        "lowercase",
                        "russian_stop",
                        "russian_stemmer"
                })
                .endObject()
                .endObject()
                .endObject()
                .endObject()
                .endObject();
    }

    private void ensureReminderIndex() throws IOException {
        if (!indexExists(REMINDER_INDEX)) {
            CreateIndexRequest request = new CreateIndexRequest(REMINDER_INDEX);

            XContentBuilder mapping = XContentFactory.jsonBuilder()
                    .startObject()
                    .startObject("properties")
                    .startObject("user_id")
                    .field("type", "keyword")
                    .endObject()
                    .startObject("original_text")
                    .field("type", "text")
                    .field("analyzer", "russian")
                    .endObject()
                    .startObject("extracted_action")
                    .field("type", "text")
                    .field("analyzer", "russian")
                    .endObject()
                    .startObject("scheduled_time")
                    .field("type", "date")
                    .field("format", "strict_date_optional_time||epoch_millis")
                    .endObject()
                    .startObject("reminder_time")
                    .field("type", "keyword")
                    .endObject()
                    .startObject("status")
                    .field("type", "keyword")
                    .endObject()
                    .startObject("notification_sent")
                    .field("type", "boolean")
                    .endObject()
                    .startObject("eventbridge_rule_name")
                    .field("type", "keyword")
                    .endObject()
                    .startObject("created_at")
                    .field("type", "date")
                    .field("format", "strict_date_optional_time||epoch_millis")
                    .endObject()
                    .startObject("updated_at")
                    .field("type", "date")
                    .field("format", "strict_date_optional_time||epoch_millis")
                    .endObject()
                    .endObject()
                    .endObject();

            request.mapping(mapping);
            var response = openSearchClient.indices().create(request, RequestOptions.DEFAULT);
            log.info("Created reminder index: {}", response.index());
        }
    }

    private boolean indexExists(String indexName) throws IOException {
        GetIndexRequest request = new GetIndexRequest(indexName);
        return openSearchClient.indices().exists(request, RequestOptions.DEFAULT);
    }

    public CompletableFuture<String> indexTranscription(TranscriptionResult transcription) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> source = new HashMap<>();
                source.put("original_audio_key", transcription.originalAudioKey());
                source.put("transcribed_text", transcription.transcribedText());
                source.put("confidence", transcription.confidence());
                source.put("language", transcription.language());
                source.put("duration_seconds", transcription.durationSeconds());
                source.put("completed_at", transcription.completedAt());
                source.put("indexed_at", LocalDateTime.now());

                IndexRequest request = new IndexRequest(TRANSCRIPTION_INDEX)
                        .id(transcription.transcriptionId())
                        .source(source, XContentType.JSON);

                var response = openSearchClient.index(request, RequestOptions.DEFAULT);
                log.info("Transcription indexed: {}", response.getId());
                return response.getId();
            } catch (IOException e) {
                log.error("Failed to index transcription", e);
                throw new RuntimeException("Failed to index transcription", e);
            }
        });
    }

    public CompletableFuture<String> indexReminder(ReminderRecord reminder) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> source = new HashMap<>();
                source.put("user_id", reminder.userId());
                source.put("original_text", reminder.originalText());
                source.put("extracted_action", reminder.extractedAction());
                source.put("scheduled_time", reminder.scheduledTime());
                source.put("reminder_time", reminder.reminderTime());
                source.put("status", reminder.status().toString());
                source.put("notification_sent", reminder.notificationSent());
                source.put("created_at", reminder.createdAt());
                source.put("updated_at", LocalDateTime.now());

                IndexRequest request = new IndexRequest(REMINDER_INDEX)
                        .id(reminder.reminderId())
                        .source(source, XContentType.JSON);

                var response = openSearchClient.index(request, RequestOptions.DEFAULT);
                log.info("Reminder indexed: {}", response.getId());
                return response.getId();
            } catch (IOException e) {
                log.error("Failed to index reminder", e);
                throw new RuntimeException("Failed to index reminder", e);
            }
        });
    }

    public CompletableFuture<List<ReminderRecord>> findRemindersByTime(LocalDateTime time) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                        .must(QueryBuilders.termQuery("status", "SCHEDULED"))
                        .must(QueryBuilders.termQuery("notification_sent", false));

                RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("scheduled_time")
                        .gte(time.minusMinutes(5))
                        .lte(time.plusMinutes(5))
                        .format("strict_date_optional_time");

                boolQuery.must(rangeQuery);
                sourceBuilder.query(boolQuery);
                sourceBuilder.size(100);
                sourceBuilder.sort("scheduled_time", SortOrder.ASC);

                SearchRequest request = new SearchRequest(REMINDER_INDEX)
                        .source(sourceBuilder);

                SearchResponse response = openSearchClient.search(request, RequestOptions.DEFAULT);

                return Arrays.stream(response.getHits().getHits())
                        .map(this::mapToReminderRecord)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

            } catch (IOException e) {
                log.error("Failed to search reminders", e);
                throw new RuntimeException("Failed to search reminders", e);
            }
        });
    }

    public CompletableFuture<List<ReminderRecord>> findRemindersByUser(String userId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
                sourceBuilder.query(QueryBuilders.termQuery("user_id", userId));
                sourceBuilder.size(limit);
                sourceBuilder.sort("scheduled_time", SortOrder.DESC);

                SearchRequest request = new SearchRequest(REMINDER_INDEX)
                        .source(sourceBuilder);

                SearchResponse response = openSearchClient.search(request, RequestOptions.DEFAULT);

                return Arrays.stream(response.getHits().getHits())
                        .map(this::mapToReminderRecord)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

            } catch (IOException e) {
                log.error("Failed to search user reminders", e);
                throw new RuntimeException("Failed to search user reminders", e);
            }
        });
    }

    public CompletableFuture<Optional<ReminderRecord>> getReminderById(String reminderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GetRequest request = new GetRequest(REMINDER_INDEX, reminderId);
                GetResponse response = openSearchClient.get(request, RequestOptions.DEFAULT);

                if (!response.isExists()) {
                    return Optional.empty();
                }

                Map<String, Object> source = response.getSourceAsMap();
                ReminderRecord record = mapToReminderRecord(source, reminderId);
                return Optional.ofNullable(record);

            } catch (IOException e) {
                log.error("Failed to get reminder by id: {}", reminderId, e);
                throw new RuntimeException("Failed to get reminder", e);
            }
        });
    }

    public CompletableFuture<Boolean> updateReminderStatus(String reminderId,
                                                           ReminderRecord.ReminderStatus status,
                                                           boolean notificationSent) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", status.toString());
                updates.put("notification_sent", notificationSent);
                updates.put("updated_at", LocalDateTime.now());

                UpdateRequest request = new UpdateRequest(REMINDER_INDEX, reminderId)
                        .doc(updates, XContentType.JSON);

                UpdateResponse response = openSearchClient.update(request, RequestOptions.DEFAULT);
                log.info("Updated reminder {} status to {}", reminderId, status);
                return response.getResult() == UpdateResponse.Result.UPDATED;

            } catch (IOException e) {
                log.error("Failed to update reminder status: {}", reminderId, e);
                throw new RuntimeException("Failed to update reminder status", e);
            }
        });
    }

    public CompletableFuture<Boolean> deleteReminder(String reminderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DeleteRequest request = new DeleteRequest(REMINDER_INDEX, reminderId);
                DeleteResponse response = openSearchClient.delete(request, RequestOptions.DEFAULT);

                log.info("Deleted reminder: {}", reminderId);
                return response.getResult() == DeleteResponse.Result.DELETED;

            } catch (IOException e) {
                log.error("Failed to delete reminder: {}", reminderId, e);
                throw new RuntimeException("Failed to delete reminder", e);
            }
        });
    }

    public CompletableFuture<List<TranscriptionResult>> searchTranscriptions(String query,
                                                                             String userId,
                                                                             int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                        .must(QueryBuilders.matchQuery("transcribed_text", query));

                if (userId != null) {
                    boolQuery.must(QueryBuilders.termQuery("user_id", userId));
                }

                sourceBuilder.query(boolQuery);
                sourceBuilder.size(limit);
                sourceBuilder.sort("completed_at", SortOrder.DESC);

                SearchRequest request = new SearchRequest(TRANSCRIPTION_INDEX)
                        .source(sourceBuilder);

                SearchResponse response = openSearchClient.search(request, RequestOptions.DEFAULT);

                return Arrays.stream(response.getHits().getHits())
                        .map(this::mapToTranscriptionResult)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

            } catch (IOException e) {
                log.error("Failed to search transcriptions", e);
                throw new RuntimeException("Failed to search transcriptions", e);
            }
        });
    }

    public CompletableFuture<Map<String, Object>> getReminderStats(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> stats = new HashMap<>();

                SearchSourceBuilder totalBuilder = new SearchSourceBuilder();
                totalBuilder.query(QueryBuilders.termQuery("user_id", userId));
                totalBuilder.size(0);

                SearchRequest totalRequest = new SearchRequest(REMINDER_INDEX)
                        .source(totalBuilder);

                SearchResponse totalResponse = openSearchClient.search(totalRequest, RequestOptions.DEFAULT);
                stats.put("total", totalResponse.getHits().getTotalHits().value);

                for (ReminderRecord.ReminderStatus status : ReminderRecord.ReminderStatus.values()) {
                    SearchSourceBuilder statusBuilder = new SearchSourceBuilder();
                    BoolQueryBuilder query = QueryBuilders.boolQuery()
                            .must(QueryBuilders.termQuery("user_id", userId))
                            .must(QueryBuilders.termQuery("status", status.toString()));

                    statusBuilder.query(query);
                    statusBuilder.size(0);

                    SearchRequest statusRequest = new SearchRequest(REMINDER_INDEX)
                            .source(statusBuilder);

                    SearchResponse statusResponse = openSearchClient.search(statusRequest, RequestOptions.DEFAULT);
                    stats.put(status.toString().toLowerCase(),
                            statusResponse.getHits().getTotalHits().value);
                }

                return stats;

            } catch (IOException e) {
                log.error("Failed to get reminder stats", e);
                throw new RuntimeException("Failed to get reminder stats", e);
            }
        });
    }

    private ReminderRecord mapToReminderRecord(SearchHit hit) {
        try {
            return mapToReminderRecord(hit.getSourceAsMap(), hit.getId());
        } catch (Exception e) {
            log.error("Failed to map SearchHit to ReminderRecord", e);
            return null;
        }
    }

    private ReminderRecord mapToReminderRecord(Map<String, Object> source, String id) {
        try {
            return new ReminderRecord(
                id,
                (String) source.get("user_id"),
                (String) source.get("original_text"),
                (String) source.get("extracted_action"),
                LocalDateTime.parse((String) source.get("scheduled_time")),
                (String) source.get("reminder_time"),
                LocalDateTime.parse((String) source.get("created_at")),
                ReminderRecord.ReminderStatus.valueOf((String) source.get("status")),
                (Boolean) source.get("notification_sent"),
                (String) source.get("rule_name")
            );
        } catch (Exception e) {
            log.error("Failed to map source to ReminderRecord", e);
            return null;
        }
    }

    public CompletableFuture<Boolean> updateReminderEventBridgeRule(
            String reminderId, String ruleName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> updates = new HashMap<>();
                updates.put("eventbridge_rule_name", ruleName);
                updates.put("updated_at", LocalDateTime.now());

                UpdateRequest request = new UpdateRequest(REMINDER_INDEX, reminderId)
                        .doc(updates, XContentType.JSON);

                UpdateResponse response = openSearchClient.update(request, RequestOptions.DEFAULT);
                return response.getResult() == UpdateResponse.Result.UPDATED;
            } catch (IOException e) {
                log.error("Failed to update reminder rule: {}", reminderId, e);
                throw new RuntimeException("Failed to update reminder rule", e);
            }
        });
    }

    private TranscriptionResult mapToTranscriptionResult(SearchHit hit) {
        try {
            Map<String, Object> source = hit.getSourceAsMap();

            return new TranscriptionResult(
                    hit.getId(),
                    (String) source.get("original_audio_key"),
                    (String) source.get("transcribed_text"),
                    source.get("confidence") != null ?
                            ((Number) source.get("confidence")).doubleValue() : null,
                    (String) source.get("language"),
                    source.get("duration_seconds") != null ?
                            ((Number) source.get("duration_seconds")).doubleValue() : null,
                    LocalDateTime.parse((String) source.get("completed_at"))
            );
        } catch (Exception e) {
            log.error("Failed to map SearchHit to TranscriptionResult", e);
            return null;
        }
    }

    public CompletableFuture<Void> cleanupIndices() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (indexExists(TRANSCRIPTION_INDEX)) {
                    DeleteIndexRequest request = new DeleteIndexRequest(TRANSCRIPTION_INDEX);
                    openSearchClient.indices().delete(request, RequestOptions.DEFAULT);
                    log.info("Deleted transcription index");
                }

                if (indexExists(REMINDER_INDEX)) {
                    DeleteIndexRequest request = new DeleteIndexRequest(REMINDER_INDEX);
                    openSearchClient.indices().delete(request, RequestOptions.DEFAULT);
                    log.info("Deleted reminder index");
                }
            } catch (IOException e) {
                log.error("Failed to cleanup indices", e);
            }
        });
    }
}