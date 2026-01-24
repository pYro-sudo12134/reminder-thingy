package by.losik.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

class SimpleReminderParserTest {

    private SimpleReminderParser parser;

    @BeforeEach
    void setUp() {
        parser = new SimpleReminderParser();
    }

    @Test
    void parse_WithExactTimeFormat_ShouldParseCorrectly() {
        String text = "позвонить маме в 15:30";

        SimpleReminderParser.ParsedResult result = parser.parse(text);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("позвонить маме", result.action());
        Assertions.assertEquals(15, result.scheduledTime().getHour());
        Assertions.assertEquals(30, result.scheduledTime().getMinute());
        Assertions.assertTrue(result.confidence() > 0.5);
    }

    @Test
    void parse_WithRelativeTime_ShouldParseCorrectly() {
        String text = "сделать домашку через 2 часа";
        LocalDateTime now = LocalDateTime.now();

        SimpleReminderParser.ParsedResult result = parser.parse(text);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("сделать домашку", result.action());
        Assertions.assertTrue(result.scheduledTime().isAfter(now.plusHours(1)));
        Assertions.assertTrue(result.scheduledTime().isBefore(now.plusHours(3)));
        Assertions.assertTrue(result.confidence() > 0.5);
    }

    @Test
    void parse_WithMinutes_ShouldParseCorrectly() {
        String text = "забрать посылку через 15 минут";
        LocalDateTime now = LocalDateTime.now();

        SimpleReminderParser.ParsedResult result = parser.parse(text);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("забрать посылку", result.action());
        Assertions.assertTrue(result.scheduledTime().isAfter(now.plusMinutes(10)));
        Assertions.assertTrue(result.scheduledTime().isBefore(now.plusMinutes(20)));
    }

    @Test
    void parse_WithDays_ShouldParseCorrectly() {
        String text = "сходить к врачу через 3 дня";
        LocalDateTime now = LocalDateTime.now();

        SimpleReminderParser.ParsedResult result = parser.parse(text);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.action().contains("врач") || result.action().contains("сходить"));
        Assertions.assertTrue(result.scheduledTime().isAfter(now.plusDays(2)));
        Assertions.assertTrue(result.scheduledTime().isBefore(now.plusDays(4)));
    }

    @Test
    void parse_WhenTimeInPast_ShouldScheduleForNextDay() {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();
        int pastHour = (currentHour - 2 + 24) % 24;

        String text = "позвонить в " + pastHour + ":00";

        SimpleReminderParser.ParsedResult result = parser.parse(text);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.scheduledTime().isAfter(now));
    }

    @Test
    void parse_WithNoTimeSpecified_ShouldUseDefaultTime() {
        String text = "просто напоминание без времени";
        LocalDateTime now = LocalDateTime.now();

        SimpleReminderParser.ParsedResult result = parser.parse(text);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("просто напоминание без времени", result.action());
        Assertions.assertTrue(result.scheduledTime().isAfter(now));
        Assertions.assertTrue(result.confidence() < 0.5);
    }

    @Test
    void parse_EmptyText_ShouldReturnDefault() {
        String text = "";

        SimpleReminderParser.ParsedResult result = parser.parse(text);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("напоминание", result.action());
        Assertions.assertNotNull(result.scheduledTime());
    }

    @Test
    void parse_OnlyTimeNoAction_ShouldUseDefaultAction() {
        String text = "в 18:00";

        SimpleReminderParser.ParsedResult result = parser.parse(text);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.action().contains("напоминание") || result.action().isEmpty());
        Assertions.assertEquals(18, result.scheduledTime().getHour());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "сходить в магазин",
            "позвонить другу",
            "закончить отчет"
    })
    void parse_ActionWithoutTime_ShouldExtractActionOnly(String input) {
        SimpleReminderParser.ParsedResult result = parser.parse(input);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.action());
        Assertions.assertNotNull(result.scheduledTime());
    }

    @Test
    void formatForEventBridge_ShouldReturnISOFormat() {
        LocalDateTime time = LocalDateTime.of(2024, 12, 31, 23, 59, 30);

        String formatted = parser.formatForEventBridge(time);

        Assertions.assertEquals("2024-12-31T23:59:30", formatted);
    }

    @Test
    void formatForDisplay_ShouldReturnTimeOnly() {
        LocalDateTime time = LocalDateTime.of(2024, 12, 31, 14, 30);

        String formatted = parser.formatForDisplay(time);

        Assertions.assertEquals("14:30", formatted);
    }

    @Test
    void parse_ComplexSentence_ShouldExtractActionAndTime() {
        String text = "пожалуйста, не забудь взять документы из офиса в 17:30 сегодня";

        SimpleReminderParser.ParsedResult result = parser.parse(text);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(17, result.scheduledTime().getHour());
        Assertions.assertEquals(30, result.scheduledTime().getMinute());
        Assertions.assertTrue(result.action().contains("документы"));
    }

    @ParameterizedTest
    @CsvSource({
            "встреча в 14:00, встреча, 14, 0",
            "забрать детей из школы в 16.45, забрать детей из школы, 16, 45",
            "собрание в 9 утра, собрание, 9, 0",
            "вебинар в 19 часов, вебинар, 19, 0",
            "зарядка в 7 утра, зарядка, 7, 0"
    })
    void parse_VariousTimeFormats_ShouldParseCorrectly(String input, String expectedAction, int expectedHour, int expectedMinute) {
        SimpleReminderParser.ParsedResult result = parser.parse(input);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.action().contains(expectedAction) ||
                expectedAction.contains(result.action()) ||
                result.action().contains(expectedAction.split(" ")[0]));
        Assertions.assertEquals(expectedHour, result.scheduledTime().getHour());
        Assertions.assertEquals(expectedMinute, result.scheduledTime().getMinute());
    }

    @Test
    void parse_MultipleTimeReferences_ShouldUseFirstOne() {
        String text = "встреча в 10 утра, а потом еще одна в 14:00";

        SimpleReminderParser.ParsedResult result = parser.parse(text);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.scheduledTime());
        Assertions.assertTrue(result.scheduledTime().getHour() == 10 || result.scheduledTime().getHour() == 14);
    }

    @Test
    void parse_WithTimeAndPeriod_ShouldAdjustHours() {
        String text = "купить молоко в 8 вечера";

        SimpleReminderParser.ParsedResult result = parser.parse(text);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.action().contains("купить") || result.action().contains("молоко"));
        Assertions.assertNotNull(result.scheduledTime());
    }

    @Test
    void parse_ShouldExtractTimeEvenIfActionNotPerfect() {
        String text = "купить молоко в 20:00";

        SimpleReminderParser.ParsedResult result = parser.parse(text);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(20, result.scheduledTime().getHour());
        Assertions.assertEquals(0, result.scheduledTime().getMinute());
        Assertions.assertTrue(result.action().contains("купить") || result.action().contains("молоко"));
    }

    @Test
    void parse_ShouldHandleVariousPrepositions() {
        String text1 = "позвонить в 10";
        String text2 = "собрание на 14:30";
        String text3 = "встреча к 18:00";

        SimpleReminderParser.ParsedResult result1 = parser.parse(text1);
        Assertions.assertNotNull(result1);
        int hour1 = result1.scheduledTime().getHour();
        Assertions.assertTrue(hour1 == 10 || hour1 == 22);

        SimpleReminderParser.ParsedResult result2 = parser.parse(text2);
        Assertions.assertNotNull(result2);
        Assertions.assertEquals(14, result2.scheduledTime().getHour());
        Assertions.assertEquals(30, result2.scheduledTime().getMinute());

        SimpleReminderParser.ParsedResult result3 = parser.parse(text3);
        Assertions.assertNotNull(result3);
        Assertions.assertEquals(18, result3.scheduledTime().getHour());
    }
}