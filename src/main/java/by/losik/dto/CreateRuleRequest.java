package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Запрос на создание правила в EventBridge.
 * @param ruleName Имя правила
 * @param scheduleTime Время выполнения (cron expression)
 * @param targetArn ARN целевой Lambda функции
 * @param inputData Данные для передачи в Lambda
 * @param description Описание правила
 * @param intent Намерение (reminder, alert, etc.)
 */
public record CreateRuleRequest(
        @JsonProperty("rule_name")
        String ruleName,

        @JsonProperty("schedule_time")
        LocalDateTime scheduleTime,

        @JsonProperty("target_arn")
        String targetArn,

        @JsonProperty("input_data")
        Map<String, Object> inputData,

        @JsonProperty("description")
        String description,

        @JsonProperty("intent")
        String intent
) {}