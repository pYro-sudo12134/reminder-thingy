package by.losik.service.mapper;

import by.losik.dto.TranscriptionResult;
import com.google.inject.Singleton;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Маппер для индекса транскрипций в OpenSearch.
 * <p>
 * Отвечает за:
 * <ul>
 *     <li>Создание маппинга индекса transcriptions</li>
 *     <li>Конвертацию TranscriptionResult в Map для индексации</li>
 *     <li>Конвертацию SearchHit в TranscriptionResult</li>
 * </ul>
 */
@Singleton
public class TranscriptionIndexMapper {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionIndexMapper.class);

    /**
     * Строит маппинг для индекса транскрипций.
     * <p>
     * Включает:
     * <ul>
     *     <li>original_audio_key как keyword</li>
     *     <li>transcribed_text как text с русским анализатором</li>
     *     <li>confidence как float</li>
     *     <li>language как keyword</li>
     *     <li>duration_seconds как float</li>
     *     <li>user_id как keyword</li>
     *     <li>completed_at и indexed_at как date</li>
     * </ul>
     *
     * @return XContentBuilder с маппингом
     * @throws IOException если не удалось создать маппинг
     */
    public XContentBuilder buildTranscriptionIndexMapping() throws IOException {
        return XContentFactory.jsonBuilder()
                .startObject()
                .startObject("mappings")
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
                .endObject()
                .endObject();
    }

    /**
     * Конвертирует TranscriptionResult в Map для индексации в OpenSearch.
     *
     * @param transcription транскрипция для конвертации
     * @return Map с полями для индексации
     */
    public Map<String, Object> toIndexSource(TranscriptionResult transcription) {
        Map<String, Object> source = new HashMap<>();
        source.put("original_audio_key", transcription.originalAudioKey());
        source.put("transcribed_text", transcription.transcribedText());
        source.put("confidence", transcription.confidence());
        source.put("language", transcription.language());
        source.put("duration_seconds", transcription.durationSeconds());
        source.put("user_id", extractUserIdFromAudioKey(transcription.originalAudioKey()));
        source.put("completed_at", transcription.completedAt());
        source.put("indexed_at", LocalDateTime.now());
        return source;
    }

    /**
     * Конвертирует Map из OpenSearch в TranscriptionResult.
     *
     * @param source Map с данными из OpenSearch
     * @param id ID транскрипции
     * @return TranscriptionResult или null если конвертация не удалась
     */
    public TranscriptionResult mapToTranscriptionResult(Map<String, Object> source, String id) {
        try {
            return new TranscriptionResult(
                    id,
                    (String) source.get("original_audio_key"),
                    (String) source.get("transcribed_text"),
                    source.get("confidence") != null ?
                            ((Number) source.get("confidence")).doubleValue() : null,
                    (String) source.get("language"),
                    source.get("duration_seconds") != null ?
                            ((Number) source.get("duration_seconds")).doubleValue() : null,
                    parseDateTime((String) source.get("completed_at"))
            );
        } catch (Exception e) {
            log.error("Failed to map source to TranscriptionResult: {}", source, e);
            return null;
        }
    }

    /**
     * Извлекает userId из ключа аудиофайла.
     * <p>
     * Формат ключа: audio/{userId}/{timestamp}/{filename}
     *
     * @param audioKey ключ аудиофайла
     * @return userId или null если не удалось извлечь
     */
    private String extractUserIdFromAudioKey(String audioKey) {
        if (audioKey == null || audioKey.isEmpty()) {
            return null;
        }

        // Формат: audio/{userId}/{timestamp}/{filename}
        String[] parts = audioKey.split("/");
        if (parts.length >= 2 && "audio".equals(parts[0])) {
            return parts[1];
        }

        return null;
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
            return LocalDateTime.now();
        }
    }
}
