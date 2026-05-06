package by.losik.service.mapper;

import by.losik.config.OpenSearchConfig;
import by.losik.dto.ReminderRecord;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class ReminderIndexMapper {

    private static final Logger log = LoggerFactory.getLogger(ReminderIndexMapper.class);

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final OpenSearchConfig config;

    @Inject
    public ReminderIndexMapper(OpenSearchConfig config) {
        this.config = config;
    }

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

    public Map<String, Object> toIndexSource(ReminderRecord reminder) {
        Map<String, Object> source = new HashMap<>();
        source.put("user_id", reminder.userId());
        source.put("user_email", reminder.userEmail());
        source.put("original_text", reminder.originalText());
        source.put("extracted_action", reminder.extractedAction());
        source.put("scheduled_time", reminder.scheduledTime() != null ? reminder.scheduledTime().format(ISO_FORMATTER) : null);
        source.put("status", reminder.status().toString());
        source.put("notification_sent", reminder.notificationSent());
        source.put("created_at", reminder.createdAt() != null ? reminder.createdAt().format(ISO_FORMATTER) : null);
        source.put("updated_at", reminder.createdAt() != null ? reminder.createdAt().format(ISO_FORMATTER) : null);
        source.put("intent", reminder.intent());
        source.put("eventbridge_rule_name", reminder.eventBridgeRuleName());
        return source;
    }

    public ReminderRecord mapToReminderRecord(Map<String, Object> source, String id) {
        try {
            return new ReminderRecord(
                    id,
                    (String) source.get("user_id"),
                    (String) source.get("user_email"),
                    (String) source.get("original_text"),
                    (String) source.get("extracted_action"),
                    parseZonedDateTime((String) source.get("scheduled_time")),
                    parseZonedDateTime((String) source.get("created_at")),
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

    private ZonedDateTime parseZonedDateTime(String dateTimeStr) {
        if (dateTimeStr == null) {
            return null;
        }

        try {
            return by.losik.util.DateTimeParser.parseZonedDateTime(dateTimeStr, java.time.ZoneOffset.UTC);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}, using current time", dateTimeStr);
            return java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
        }
    }
}
