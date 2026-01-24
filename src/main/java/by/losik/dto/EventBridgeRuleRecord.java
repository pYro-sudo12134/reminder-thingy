package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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