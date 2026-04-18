package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Запись о правиле EventBridge.
 * @param ruleName Имя правила
 * @param scheduleExpression Cron выражение для расписания
 * @param targetLambdaArn ARN целевой Lambda функции
 * @param enabled Включено ли правило
 * @param description Описание правила
 * @param targetInput Входные данные для Lambda
 */
public record EventBridgeRuleRecord(
        @JsonProperty("rule_name")
        String ruleName,

        @JsonProperty("schedule_expression")
        String scheduleExpression,

        @JsonProperty("target_lambda_arn")
        String targetLambdaArn,

        @JsonProperty("enabled")
        boolean enabled,

        @JsonProperty("description")
        String description,

        @JsonProperty("target_input")
        String targetInput
) {}