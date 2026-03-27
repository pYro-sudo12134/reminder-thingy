package by.losik.service.mapper;

import by.losik.config.OpenSearchConfig;
import by.losik.dto.ReminderRecord;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Маппер для индекса напоминаний в OpenSearch.
 * <p>
 * Отвечает за:
 * <ul>
 *     <li>Создание маппинга индекса reminders</li>
 *     <li>Конвертацию ReminderRecord в Map для индексации</li>
 *     <li>Конвертацию SearchHit в ReminderRecord</li>
 * </ul>
 *
 * @see OpenSearchConfig
 */
public class ReminderIndexMapper {

    private static final Logger log = LoggerFactory.getLogger(ReminderIndexMapper.class);

    private final OpenSearchConfig config;

    /**
     * Создаёт маппер с конфигурацией.
     *
     * @param config конфигурация OpenSearch
     */
    public ReminderIndexMapper(OpenSearchConfig config) {
        this.config = config;
    }

    /**
     * Строит маппинг для индекса напоминаний.
     * <p>
     * Включает:
     * <ul>
     *     <li>autocomplete_analyzer с edge_ngram фильтром</li>
     *     <li>Русский анализатор для original_text и extracted_action</li>
     *     <li>Keyword поля для user_id, status, eventbridge_rule_name</li>
     *     <li>Date поля для scheduled_time, created_at, updated_at</li>
     * </ul>
     *
     * @return XContentBuilder с маппингом
     * @throws IOException если не удалось создать маппинг
     */
    public XContentBuilder buildReminderIndexMapping() throws IOException {
        return XContentFactory.jsonBuilder()
                .startObject()
                .startObject("settings")
                .startObject("analysis")
                .startObject("analyzer")
                .startObject("autocomplete_analyzer")
                .field("type", "custom")
                .field("tokenizer", "standard")
                .field("filter", new String[]{"lowercase", "autocomplete_filter"})
                .endObject()
                .startObject("autocomplete_search_analyzer")
                .field("type", "custom")
                .field("tokenizer", "standard")
                .field("filter", new String[]{"lowercase"})
                .endObject()
                .endObject()
                .startObject("filter")
                .startObject("autocomplete_filter")
                .field("type", "edge_ngram")
                .field("min_gram", config.getAutocompleteMinGram())
                .field("max_gram", config.getAutocompleteMaxGram())
                .endObject()
                .endObject()
                .endObject()
                .endObject()
                .startObject("mappings")
                .startObject("properties")
                .startObject("user_id")
                .field("type", "keyword")
                .endObject()
                .startObject("user_email")
                .field("type", "keyword")
                .endObject()
                .startObject("original_text")
                .field("type", "text")
                .field("analyzer", "russian")
                .field("search_analyzer", "russian")
                .startObject("fields")
                .startObject("autocomplete")
                .field("type", "text")
                .field("analyzer", "autocomplete_analyzer")
                .field("search_analyzer", "autocomplete_search_analyzer")
                .endObject()
                .endObject()
                .endObject()
                .startObject("extracted_action")
                .field("type", "text")
                .field("analyzer", "russian")
                .field("search_analyzer", "russian")
                .startObject("fields")
                .startObject("autocomplete")
                .field("type", "text")
                .field("analyzer", "autocomplete_analyzer")
                .field("search_analyzer", "autocomplete_search_analyzer")
                .endObject()
                .endObject()
                .endObject()
                .startObject("scheduled_time")
                .field("type", "date")
                .field("format", "strict_date_optional_time||epoch_millis")
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
                .startObject("intent")
                .field("type", "keyword")
                .endObject()
                .endObject()
                .endObject()
                .endObject();
    }

    /**
     * Конвертирует ReminderRecord в Map для индексации в OpenSearch.
     *
     * @param reminder напоминание для конвертации
     * @return Map с полями для индексации
     */
    public Map<String, Object> toIndexSource(ReminderRecord reminder) {
        Map<String, Object> source = new HashMap<>();
        source.put("user_id", reminder.userId());
        source.put("user_email", reminder.userEmail());
        source.put("original_text", reminder.originalText());
        source.put("extracted_action", reminder.extractedAction());
        source.put("scheduled_time", reminder.scheduledTime());
        source.put("status", reminder.status().toString());
        source.put("notification_sent", reminder.notificationSent());
        source.put("created_at", reminder.createdAt());
        source.put("updated_at", reminder.createdAt());
        source.put("intent", reminder.intent());
        source.put("eventbridge_rule_name", reminder.eventBridgeRuleName());
        return source;
    }

    /**
     * Конвертирует Map из OpenSearch в ReminderRecord.
     *
     * @param source Map с данными из OpenSearch
     * @param id ID напоминания
     * @return ReminderRecord или null если конвертация не удалась
     */
    public ReminderRecord mapToReminderRecord(Map<String, Object> source, String id) {
        try {
            return new ReminderRecord(
                    id,
                    (String) source.get("user_id"),
                    (String) source.get("user_email"),
                    (String) source.get("original_text"),
                    (String) source.get("extracted_action"),
                    parseDateTime((String) source.get("scheduled_time")),
                    parseDateTime((String) source.get("created_at")),
                    ReminderRecord.ReminderStatus.valueOf((String) source.get("status")),
                    (Boolean) source.get("notification_sent"),
                    (String) source.get("intent"),
                    (String) source.get("eventbridge_rule_name")
            );
        } catch (Exception e) {
            log.error("Failed to map source to ReminderRecord: {}", source, e);
            return null;
        }
    }

    /**
     * Парсит строку в LocalDateTime.
     *
     * @param dateTimeStr строка с датой и временем
     * @return LocalDateTime или null если парсинг не удался
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null) {
            return null;
        }

        try {
            return by.losik.util.DateTimeParser.parseLocalDateTime(dateTimeStr);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}, using current time", dateTimeStr);
            return java.time.LocalDateTime.now();
        }
    }
}
