package by.losik.config;

import by.losik.util.ConfigUtils;
import com.google.inject.Singleton;

/**
 * Конфигурация OpenSearch.
 * <p>
 * Определяет настройки для работы с поисковым движком OpenSearch:
 * <ul>
 *     <li>Имена индексов для напоминаний и транскрипций</li>
 *     <li>Параметры autocomplete (min_gram, max_gram)</li>
 *     <li>Лимиты поиска по умолчанию</li>
 * </ul>
 * <p>
 * Настройки загружаются из переменных окружения через ConfigUtils:
 * <ul>
 *     <li>{@code OPENSEARCH_REMINDER_INDEX} — имя индекса напоминаний (по умолчанию "reminders")</li>
 *     <li>{@code OPENSEARCH_TRANSCRIPTION_INDEX} — имя индекса транскрипций (по умолчанию "transcriptions")</li>
 *     <li>{@code OPENSEARCH_AUTOCOMPLETE_MIN_GRAM} — минимальная длина edge_ngram (по умолчанию 2)</li>
 *     <li>{@code OPENSEARCH_AUTOCOMPLETE_MAX_GRAM} — максимальная длина edge_ngram (по умолчанию 10)</li>
 *     <li>{@code OPENSEARCH_DEFAULT_SEARCH_LIMIT} — лимит поиска по умолчанию (по умолчанию 10)</li>
 * </ul>
 *
 * @see by.losik.service.OpenSearchService
 */
@Singleton
public class OpenSearchConfig {

    /** Имя индекса напоминаний по умолчанию */
    private static final String DEFAULT_REMINDER_INDEX = "reminders";

    /** Имя индекса транскрипций по умолчанию */
    private static final String DEFAULT_TRANSCRIPTION_INDEX = "transcriptions";

    /** Минимальная длина edge_ngram по умолчанию */
    private static final int DEFAULT_AUTOCOMPLETE_MIN_GRAM = 2;

    /** Максимальная длина edge_ngram по умолчанию */
    private static final int DEFAULT_AUTOCOMPLETE_MAX_GRAM = 10;

    /** Лимит поиска по умолчанию */
    private static final int DEFAULT_SEARCH_LIMIT = 10;

    private final String reminderIndexName;
    private final String transcriptionIndexName;
    private final int autocompleteMinGram;
    private final int autocompleteMaxGram;
    private final int defaultSearchLimit;

    /**
     * Создаёт конфигурацию OpenSearch с загрузкой настроек из переменных окружения.
     */
    public OpenSearchConfig() {
        this.reminderIndexName = ConfigUtils.getEnvOrDefault("OPENSEARCH_REMINDER_INDEX", DEFAULT_REMINDER_INDEX);
        this.transcriptionIndexName = ConfigUtils.getEnvOrDefault("OPENSEARCH_TRANSCRIPTION_INDEX", DEFAULT_TRANSCRIPTION_INDEX);
        this.autocompleteMinGram = ConfigUtils.getIntEnvOrDefault("OPENSEARCH_AUTOCOMPLETE_MIN_GRAM", DEFAULT_AUTOCOMPLETE_MIN_GRAM);
        this.autocompleteMaxGram = ConfigUtils.getIntEnvOrDefault("OPENSEARCH_AUTOCOMPLETE_MAX_GRAM", DEFAULT_AUTOCOMPLETE_MAX_GRAM);
        this.defaultSearchLimit = ConfigUtils.getIntEnvOrDefault("OPENSEARCH_DEFAULT_SEARCH_LIMIT", DEFAULT_SEARCH_LIMIT);
    }

    /**
     * Получает имя индекса напоминаний.
     *
     * @return имя индекса (например, "reminders")
     */
    public String getReminderIndexName() {
        return reminderIndexName;
    }

    /**
     * Получает имя индекса транскрипций.
     *
     * @return имя индекса (например, "transcriptions")
     */
    public String getTranscriptionIndexName() {
        return transcriptionIndexName;
    }

    /**
     * Получает минимальную длину edge_ngram для autocomplete.
     *
     * @return минимальная длина (по умолчанию 2)
     */
    public int getAutocompleteMinGram() {
        return autocompleteMinGram;
    }

    /**
     * Получает максимальную длину edge_ngram для autocomplete.
     *
     * @return максимальная длина (по умолчанию 10)
     */
    public int getAutocompleteMaxGram() {
        return autocompleteMaxGram;
    }

    /**
     * Получает лимит поиска по умолчанию.
     *
     * @return лимит поиска (по умолчанию 10)
     */
    public int getDefaultSearchLimit() {
        return defaultSearchLimit;
    }
}
