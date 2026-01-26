package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;

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