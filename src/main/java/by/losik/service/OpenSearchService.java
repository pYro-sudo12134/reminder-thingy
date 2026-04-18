package by.losik.service;

import by.losik.config.LocalStackConfig;
import by.losik.config.OpenSearchConfig;
import by.losik.dto.AutocompleteResult;
import by.losik.dto.ReminderRecord;
import by.losik.dto.TranscriptionResult;
import by.losik.service.mapper.ReminderIndexMapper;
import by.losik.service.mapper.TranscriptionIndexMapper;
import by.losik.util.DateTimeParser;
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
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightField;
import org.opensearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Сервис для работы с OpenSearch (поисковый движок).
 * <p>
 * Предоставляет методы для:
 * <ul>
 *     <li>Инициализации индексов (reminders, transcriptions)</li>
 *     <li>Индексации напоминаний и транскрипций</li>
 *     <li>Поиска напоминаний по пользователю, времени, статусу</li>
 *     <li>Autocomplete для напоминаний с подсветкой совпадений</li>
 *     <li>Обновления и удаления напоминаний</li>
 *     <li>Статистики по напоминаниям пользователя</li>
 * </ul>
 * <p>
 * Использует асинхронные операции через CompletableFuture для всех методов.
 *
 * @see OpenSearchConfig
 * @see ReminderIndexMapper
 * @see TranscriptionIndexMapper
 */
@Singleton
public class OpenSearchService {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchService.class);
    private final RestHighLevelClient openSearchClient;
    private final OpenSearchConfig config;
    private final ReminderIndexMapper reminderMapper;
    private final TranscriptionIndexMapper transcriptionMapper;

    /**
     * Создаёт OpenSearch сервис с конфигурацией и мапперами.
     *
     * @param localStackConfig конфигурация LocalStack для клиента
     * @param config конфигурация OpenSearch
     * @param reminderMapper маппер для напоминаний
     * @param transcriptionMapper маппер для транскрипций
     */
    @Inject
    public OpenSearchService(LocalStackConfig localStackConfig,
                             OpenSearchConfig config,
                             ReminderIndexMapper reminderMapper,
                             TranscriptionIndexMapper transcriptionMapper) {
        this.openSearchClient = localStackConfig.getOpenSearchClient();
        this.config = config;
        this.reminderMapper = reminderMapper;
        this.transcriptionMapper = transcriptionMapper;
    }

    /**
     * Инициализирует индексы OpenSearch.
     * <p>
     * Создаёт индексы reminders и transcriptions, если они не существуют.
     *
     * @return CompletableFuture для асинхронного ожидания
     */
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

    /**
     * Создаёт индекс напоминаний, если он не существует.
     *
     * @throws IOException если не удалось создать индекс
     */
    private void ensureReminderIndex() throws IOException {
        String indexName = config.getReminderIndexName();
        if (!indexExists(indexName)) {
            CreateIndexRequest request = new CreateIndexRequest(indexName);
            XContentBuilder mapping = reminderMapper.buildReminderIndexMapping();
            request.source(mapping);

            var response = openSearchClient.indices().create(request, RequestOptions.DEFAULT);
            if (response != null && response.index() != null) {
                log.info("Created reminder index with autocomplete support: {}", response.index());
            } else {
                log.warn("Create reminder index response was null");
            }
        }
    }

    /**
     * Создаёт индекс транскрипций, если он не существует.
     *
     * @throws IOException если не удалось создать индекс
     */
    private void ensureTranscriptionIndex() throws IOException {
        String indexName = config.getTranscriptionIndexName();
        if (!indexExists(indexName)) {
            CreateIndexRequest request = new CreateIndexRequest(indexName);
            XContentBuilder mapping = transcriptionMapper.buildTranscriptionIndexMapping();
            request.source(mapping);

            var response = openSearchClient.indices().create(request, RequestOptions.DEFAULT);
            if (response != null && response.index() != null) {
                log.info("Created transcription index: {}", response.index());
            } else {
                log.warn("Create transcription index response was null");
            }
        }
    }

    private boolean indexExists(String indexName) throws IOException {
        GetIndexRequest request = new GetIndexRequest(indexName);
        return openSearchClient.indices().exists(request, RequestOptions.DEFAULT);
    }

    /**
     * Индексирует транскрипцию в OpenSearch.
     *
     * @param transcription транскрипция для индексации
     * @return ID индексированной транскрипции
     */
    public CompletableFuture<String> indexTranscription(TranscriptionResult transcription) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> source = transcriptionMapper.toIndexSource(transcription);
                String indexName = config.getTranscriptionIndexName();

                IndexRequest request = new IndexRequest(indexName)
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

    /**
     * Индексирует напоминание в OpenSearch.
     *
     * @param reminder напоминание для индексации
     * @return ID индексированного напоминания
     */
    public CompletableFuture<String> indexReminder(ReminderRecord reminder) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> source = reminderMapper.toIndexSource(reminder);
                String indexName = config.getReminderIndexName();

                IndexRequest request = new IndexRequest(indexName)
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

    /**
     * Находит напоминания по времени выполнения.
     *
     * @param time время для поиска
     * @return список напоминаний в диапазоне ±5 минут от времени
     */
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

                SearchRequest request = new SearchRequest(config.getReminderIndexName())
                        .source(sourceBuilder);

                SearchResponse response = openSearchClient.search(request, RequestOptions.DEFAULT);

                return Arrays.stream(response.getHits().getHits())
                        .map(hit -> reminderMapper.mapToReminderRecord(hit.getSourceAsMap(), hit.getId()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

            } catch (IOException e) {
                log.error("Failed to search reminders", e);
                throw new RuntimeException("Failed to search reminders", e);
            }
        });
    }

    /**
     * Находит напоминания пользователя.
     *
     * @param userId ID пользователя
     * @param limit максимальное количество результатов
     * @return список напоминаний пользователя
     */
    public CompletableFuture<List<ReminderRecord>> findRemindersByUser(String userId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
                sourceBuilder.query(QueryBuilders.termQuery("user_id", userId));
                sourceBuilder.size(limit);
                sourceBuilder.sort("scheduled_time", SortOrder.DESC);

                SearchRequest request = new SearchRequest(config.getReminderIndexName())
                        .source(sourceBuilder);

                SearchResponse response = openSearchClient.search(request, RequestOptions.DEFAULT);

                return Arrays.stream(response.getHits().getHits())
                        .map(hit -> reminderMapper.mapToReminderRecord(hit.getSourceAsMap(), hit.getId()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

            } catch (IOException e) {
                log.error("Failed to search user reminders", e);
                throw new RuntimeException("Failed to search user reminders", e);
            }
        });
    }

    /**
     * Получает напоминание по ID.
     *
     * @param reminderId ID напоминания
     * @return Optional с напоминанием или пустой, если не найдено
     */
    public CompletableFuture<Optional<ReminderRecord>> getReminderById(String reminderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GetRequest request = new GetRequest(config.getReminderIndexName(), reminderId);
                GetResponse response = openSearchClient.get(request, RequestOptions.DEFAULT);

                if (!response.isExists()) {
                    return Optional.empty();
                }

                Map<String, Object> source = response.getSourceAsMap();
                ReminderRecord record = reminderMapper.mapToReminderRecord(source, reminderId);
                return Optional.ofNullable(record);

            } catch (IOException e) {
                log.error("Failed to get reminder by id: {}", reminderId, e);
                throw new RuntimeException("Failed to get reminder", e);
            }
        });
    }

    /**
     * Обновляет статус напоминания.
     *
     * @param reminderId ID напоминания
     * @param status новый статус
     * @param notificationSent флаг отправки уведомления
     * @return true если обновлено успешно
     */
    public CompletableFuture<Boolean> updateReminderStatus(String reminderId,
                                                           ReminderRecord.ReminderStatus status,
                                                           boolean notificationSent) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", status.toString());
                updates.put("notification_sent", notificationSent);
                updates.put("updated_at", LocalDateTime.now());

                UpdateRequest request = new UpdateRequest(config.getReminderIndexName(), reminderId)
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

    /**
     * Удаляет напоминание.
     *
     * @param reminderId ID напоминания
     * @return true если удалено успешно
     */
    public CompletableFuture<Boolean> deleteReminder(String reminderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DeleteRequest request = new DeleteRequest(config.getReminderIndexName(), reminderId);
                DeleteResponse response = openSearchClient.delete(request, RequestOptions.DEFAULT);

                log.info("Deleted reminder: {}", reminderId);
                return response.getResult() == DeleteResponse.Result.DELETED;

            } catch (IOException e) {
                log.error("Failed to delete reminder: {}", reminderId, e);
                throw new RuntimeException("Failed to delete reminder", e);
            }
        });
    }

    /**
     * Ищет транскрипции по тексту.
     *
     * @param query поисковый запрос
     * @param userId ID пользователя (опционально)
     * @param limit максимальное количество результатов
     * @return список транскрипций
     */
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

                SearchRequest request = new SearchRequest(config.getTranscriptionIndexName())
                        .source(sourceBuilder);

                SearchResponse response = openSearchClient.search(request, RequestOptions.DEFAULT);

                return Arrays.stream(response.getHits().getHits())
                        .map(hit -> transcriptionMapper.mapToTranscriptionResult(hit.getSourceAsMap(), hit.getId()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

            } catch (IOException e) {
                log.error("Failed to search transcriptions", e);
                throw new RuntimeException("Failed to search transcriptions", e);
            }
        });
    }

    /**
     * Получает статистику по напоминаниям пользователя.
     *
     * @param userId ID пользователя
     * @return карта со статистикой по статусам
     */
    public CompletableFuture<Map<String, Object>> getReminderStats(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> stats = new HashMap<>();

                SearchSourceBuilder totalBuilder = new SearchSourceBuilder();
                totalBuilder.query(QueryBuilders.termQuery("user_id", userId));
                totalBuilder.size(0);

                SearchRequest totalRequest = new SearchRequest(config.getReminderIndexName())
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

                    SearchRequest statusRequest = new SearchRequest(config.getReminderIndexName())
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

    /**
     * Обновляет правило EventBridge для напоминания.
     *
     * @param reminderId ID напоминания
     * @param ruleName новое имя правила
     * @return true если обновлено успешно
     */
    public CompletableFuture<Boolean> updateReminderEventBridgeRule(
            String reminderId, String ruleName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> updates = new HashMap<>();
                updates.put("eventbridge_rule_name", ruleName);
                updates.put("updated_at", LocalDateTime.now());

                UpdateRequest request = new UpdateRequest(config.getReminderIndexName(), reminderId)
                        .doc(updates, XContentType.JSON);

                UpdateResponse response = openSearchClient.update(request, RequestOptions.DEFAULT);
                return response.getResult() == UpdateResponse.Result.UPDATED;
            } catch (IOException e) {
                log.error("Failed to update reminder rule: {}", reminderId, e);
                throw new RuntimeException("Failed to update reminder rule", e);
            }
        });
    }

    /**
     * Автодополнение напоминаний с подсветкой совпадений.
     *
     * @param userId ID пользователя
     * @param query поисковый запрос
     * @param limit максимальное количество результатов
     * @return результат автодополнения
     */
    public CompletableFuture<AutocompleteResult> autocompleteReminders(
            String userId, String query, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                        .must(QueryBuilders.termQuery("user_id", userId))
                        .should(QueryBuilders.matchQuery("extracted_action.autocomplete", query).boost(2.0f))
                        .should(QueryBuilders.matchQuery("original_text.autocomplete", query).boost(1.5f))
                        .should(QueryBuilders.matchQuery("extracted_action", query).boost(1.0f))
                        .should(QueryBuilders.matchQuery("original_text", query).boost(0.8f));

                sourceBuilder.query(boolQuery);
                sourceBuilder.size(limit);
                sourceBuilder.sort("_score", SortOrder.DESC);
                sourceBuilder.sort("created_at", SortOrder.DESC);

                HighlightBuilder highlightBuilder = new HighlightBuilder();
                highlightBuilder.preTags("");
                highlightBuilder.field("extracted_action");
                highlightBuilder.field("original_text");
                highlightBuilder.postTags("");
                highlightBuilder.fragmentSize(50);
                highlightBuilder.numOfFragments(1);
                sourceBuilder.highlighter(highlightBuilder);

                SearchRequest request = new SearchRequest(config.getReminderIndexName())
                        .source(sourceBuilder);

                SearchResponse response = openSearchClient.search(request, RequestOptions.DEFAULT);

                List<AutocompleteResult.Suggestion> suggestions =
                        Arrays.stream(response.getHits().getHits())
                                .map(hit -> {
                                    Map<String, Object> source = hit.getSourceAsMap();
                                    String action = (String) source.get("extracted_action");
                                    String text = (String) source.get("original_text");

                                    Map<String, HighlightField> highlights = hit.getHighlightFields();
                                    if (highlights != null && !highlights.isEmpty()) {
                                        HighlightField highlight = highlights.values().iterator().next();
                                        if (highlight != null && highlight.getFragments() != null) {
                                            action = highlight.getFragments()[0].string();
                                        }
                                    }

                                    return new AutocompleteResult.Suggestion(
                                            hit.getId(),
                                            action != null ? action : "",
                                            text != null ? text : "",
                                            hit.getScore()
                                    );
                                })
                                .collect(Collectors.toList());

                return new AutocompleteResult(
                        userId,
                        query,
                        suggestions,
                        (int) response.getHits().getTotalHits().value
                );

            } catch (IOException e) {
                log.error("Failed to autocomplete reminders", e);
                throw new RuntimeException("Failed to autocomplete reminders", e);
            }
        });
    }

    /**
     * Обновляет напоминание в OpenSearch.
     *
     * @param reminder напоминание для обновления
     * @return true если обновлено успешно
     */
    public CompletableFuture<Boolean> updateReminder(ReminderRecord reminder) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> source = reminderMapper.toIndexSource(reminder);
                String indexName = config.getReminderIndexName();

                IndexRequest request = new IndexRequest(indexName)
                        .id(reminder.reminderId())
                        .source(source, XContentType.JSON);

                var response = openSearchClient.index(request, RequestOptions.DEFAULT);
                log.info("Reminder updated: {}", response.getId());
                return true;
            } catch (IOException e) {
                log.error("Failed to update reminder: {}", reminder.reminderId(), e);
                throw new RuntimeException("Failed to update reminder", e);
            }
        });
    }

    /**
     * Находит напоминания по диапазону времени.
     *
     * @param userId ID пользователя
     * @param startTime начало диапазона
     * @param endTime конец диапазона
     * @param limit максимальное количество результатов
     * @return список напоминаний в диапазоне
     */
    public CompletableFuture<List<ReminderRecord>> findRemindersByTimeRange(
            String userId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

                if (userId != null && !userId.isBlank()) {
                    boolQuery.must(QueryBuilders.termQuery("user_id", userId));
                }

                RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("scheduled_time")
                        .gte(startTime.format(DateTimeFormatter.ISO_DATE_TIME))
                        .lte(endTime.format(DateTimeFormatter.ISO_DATE_TIME));

                boolQuery.must(rangeQuery);
                sourceBuilder.query(boolQuery);
                sourceBuilder.size(limit);
                sourceBuilder.sort("scheduled_time", SortOrder.ASC);

                SearchRequest request = new SearchRequest(config.getReminderIndexName())
                        .source(sourceBuilder);

                SearchResponse response = openSearchClient.search(request, RequestOptions.DEFAULT);

                return Arrays.stream(response.getHits().getHits())
                        .map(hit -> reminderMapper.mapToReminderRecord(hit.getSourceAsMap(), hit.getId()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

            } catch (IOException e) {
                log.error("Failed to search reminders by time range", e);
                throw new RuntimeException("Failed to search reminders by time range", e);
            }
        });
    }
}