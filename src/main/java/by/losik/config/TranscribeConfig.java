package by.losik.config;

import by.losik.util.ConfigUtils;
import com.google.inject.Singleton;

/**
 * Конфигурация AWS Transcribe.
 * <p>
 * Определяет настройки для сервиса транскрибации аудио:
 * <ul>
 *     <li>Язык распознавания речи</li>
 *     <li>Формат аудиофайлов</li>
 *     <li>Параметры polling (количество попыток, интервал)</li>
 *     <li>Таймаут на скачивание транскрипции</li>
 * </ul>
 * <p>
 * Настройки загружаются из переменных окружения через ConfigUtils:
 * <ul>
 *     <li>{@code TRANSCRIBE_LANGUAGE} — язык распознавания (по умолчанию "ru-RU")</li>
 *     <li>{@code TRANSCRIBE_FORMAT} — формат аудио (по умолчанию "wav")</li>
 *     <li>{@code TRANSCRIBE_MAX_ATTEMPTS} — макс. количество попыток polling (по умолчанию 180)</li>
 *     <li>{@code TRANSCRIBE_POLL_INTERVAL_MS} — интервал polling в мс (по умолчанию 2000)</li>
 *     <li>{@code TRANSCRIBE_TIMEOUT_SEC} — таймаут на скачивание в секундах (по умолчанию 30)</li>
 * </ul>
 *
 * @see by.losik.service.TranscribeService
 */
@Singleton
public class TranscribeConfig {

    /** Язык распознавания по умолчанию */
    private static final String DEFAULT_LANGUAGE_CODE = "ru-RU";

    /** Формат аудио по умолчанию */
    private static final String DEFAULT_MEDIA_FORMAT = "wav";

    /** Макс. количество попыток polling по умолчанию */
    private static final int DEFAULT_MAX_POLL_ATTEMPTS = 180;

    /** Интервал polling по умолчанию (2 секунды) */
    private static final long DEFAULT_POLL_INTERVAL_MS = 2000L;

    /** Таймаут на скачивание по умолчанию (30 секунд) */
    private static final int DEFAULT_TRANSCRIPTION_TIMEOUT_SEC = 30;

    private final String languageCode;
    private final String mediaFormat;
    private final int maxPollAttempts;
    private final long pollIntervalMs;
    private final int transcriptionTimeoutSec;

    /**
     * Создаёт конфигурацию Transcribe с загрузкой настроек из переменных окружения.
     */
    public TranscribeConfig() {
        this.languageCode = ConfigUtils.getEnvOrDefault("TRANSCRIBE_LANGUAGE", DEFAULT_LANGUAGE_CODE);
        this.mediaFormat = ConfigUtils.getEnvOrDefault("TRANSCRIBE_FORMAT", DEFAULT_MEDIA_FORMAT);
        this.maxPollAttempts = ConfigUtils.getIntEnvOrDefault("TRANSCRIBE_MAX_ATTEMPTS", DEFAULT_MAX_POLL_ATTEMPTS);
        this.pollIntervalMs = ConfigUtils.getLongEnvOrDefault("TRANSCRIBE_POLL_INTERVAL_MS", DEFAULT_POLL_INTERVAL_MS);
        this.transcriptionTimeoutSec = ConfigUtils.getIntEnvOrDefault("TRANSCRIBE_TIMEOUT_SEC", DEFAULT_TRANSCRIPTION_TIMEOUT_SEC);
    }

    /**
     * Получает код языка для распознавания речи.
     *
     * @return код языка (например, "ru-RU", "en-US")
     */
    public String getLanguageCode() {
        return languageCode;
    }

    /**
     * Получает формат аудиофайлов.
     *
     * @return формат аудио (например, "wav", "mp3", "flac")
     */
    public String getMediaFormat() {
        return mediaFormat;
    }

    /**
     * Получает максимальное количество попыток polling статуса транскрипции.
     *
     * @return макс. количество попыток (по умолчанию 180)
     */
    public int getMaxPollAttempts() {
        return maxPollAttempts;
    }

    /**
     * Получает интервал между попытками polling в миллисекундах.
     *
     * @return интервал в мс (по умолчанию 2000)
     */
    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    /**
     * Получает таймаут на скачивание транскрипции в секундах.
     *
     * @return таймаут в секундах (по умолчанию 30)
     */
    public int getTranscriptionTimeoutSec() {
        return transcriptionTimeoutSec;
    }
}
