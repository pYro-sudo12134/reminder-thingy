package by.losik.service;

import by.losik.config.LocalStackConfig;
import by.losik.dto.ReminderRecord;
import by.losik.dto.TranscriptionResult;
import org.apache.lucene.search.TotalHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.IndicesClient;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.Aggregations;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenSearchServiceTest {

    @Mock
    private LocalStackConfig config;

    @Mock
    private RestHighLevelClient openSearchClient;

    @Mock
    private IndicesClient indicesClient;

    private OpenSearchService openSearchService;

    private final String testTranscriptionId = "trans-123";
    private final String testReminderId = "rem-456";
    private final String testUserId = "user-789";
    private final String testAudioKey = "audio/sample.mp3";

    @BeforeEach
    void setUp() {
        when(config.getOpenSearchClient()).thenReturn(openSearchClient);
        when(openSearchClient.indices()).thenReturn(indicesClient);
        openSearchService = new OpenSearchService(config);
    }

    @Test
    void initializeIndices_Success() throws Exception {
        when(indicesClient.exists(any(GetIndexRequest.class), any(RequestOptions.class)))
                .thenReturn(false);

        ArgumentCaptor<CreateIndexRequest> requestCaptor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        openSearchService.initializeIndices().get();

        verify(indicesClient, times(2)).create(requestCaptor.capture(), any(RequestOptions.class));
        List<CreateIndexRequest> allRequests = requestCaptor.getAllValues();
        assertEquals(2, allRequests.size());
        assertTrue(allRequests.stream()
                .map(CreateIndexRequest::index)
                .allMatch(index -> index.equals("transcriptions") || index.equals("reminders")));
    }

    @Test
    void initializeIndices_IndicesAlreadyExist() throws Exception {
        when(indicesClient.exists(any(), any())).thenReturn(true);
        openSearchService.initializeIndices().get();
        verify(indicesClient, never()).create(any(), any());
    }

    @Test
    void initializeIndices_IOException_ThrowsRuntimeException() throws Exception {
        when(indicesClient.exists(any(), any())).thenThrow(new IOException("Connection failed"));
        ExecutionException exception = assertThrows(ExecutionException.class, () ->
                openSearchService.initializeIndices().get());
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertEquals("Failed to initialize indices", exception.getCause().getMessage());
    }

    @Test
    void indexTranscription_Success() throws Exception {
        TranscriptionResult transcription = new TranscriptionResult(
                testTranscriptionId,
                testAudioKey,
                "тестовый текст транскрипции",
                0.95,
                "ru-RU",
                30.5,
                LocalDateTime.now().minusHours(1)
        );

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(openSearchClient.index(any(), any())).thenReturn(indexResponse);
        when(indexResponse.getId()).thenReturn(testTranscriptionId);

        String result = openSearchService.indexTranscription(transcription).get();
        assertEquals(testTranscriptionId, result);
        verify(openSearchClient).index(any(), any());
    }

    @Test
    void indexTranscription_IOException_ThrowsRuntimeException() throws Exception {
        TranscriptionResult transcription = new TranscriptionResult(
                testTranscriptionId,
                testAudioKey,
                "тестовый текст транскрипции",
                0.95,
                "ru-RU",
                30.5,
                LocalDateTime.now().minusHours(1)
        );

        when(openSearchClient.index(any(), any())).thenThrow(new IOException("Connection failed"));
        ExecutionException exception = assertThrows(ExecutionException.class, () ->
                openSearchService.indexTranscription(transcription).get());
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertEquals("Failed to index transcription", exception.getCause().getMessage());
    }

    @Test
    void indexReminder_Success() throws Exception {
        ReminderRecord reminder = new ReminderRecord(
                testReminderId,
                testUserId,
                "Напомни завтра купить молоко",
                "купить молоко",
                LocalDateTime.now().plusDays(1),
                "утро",
                LocalDateTime.now(),
                ReminderRecord.ReminderStatus.SCHEDULED,
                false,
                "rule-123"
        );

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(openSearchClient.index(any(), any())).thenReturn(indexResponse);
        when(indexResponse.getId()).thenReturn(testReminderId);

        String result = openSearchService.indexReminder(reminder).get();
        assertEquals(testReminderId, result);
        verify(openSearchClient).index(any(), any());
    }

    @Test
    void findRemindersByTime_FoundReminders() throws Exception {
        LocalDateTime searchTime = LocalDateTime.now();
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("user_id", testUserId);
        sourceMap.put("original_text", "Напомни завтра купить молоко");
        sourceMap.put("extracted_action", "купить молоко");
        sourceMap.put("scheduled_time", LocalDateTime.now().plusDays(1).toString());
        sourceMap.put("reminder_time", "утро");
        sourceMap.put("status", "SCHEDULED");
        sourceMap.put("notification_sent", false);
        sourceMap.put("created_at", LocalDateTime.now().toString());
        sourceMap.put("eventbridge_rule_name", "rule-123");

        SearchHit searchHit = mock(SearchHit.class);
        when(searchHit.getId()).thenReturn(testReminderId);
        when(searchHit.getSourceAsMap()).thenReturn(sourceMap);

        SearchHits searchHits = mock(SearchHits.class);
        when(searchHits.getHits()).thenReturn(new SearchHit[]{searchHit});

        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(searchHits);
        when(openSearchClient.search(any(), any())).thenReturn(searchResponse);

        List<ReminderRecord> result = openSearchService.findRemindersByTime(searchTime).get();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testReminderId, result.get(0).reminderId());
    }

    @Test
    void findRemindersByTime_NoRemindersFound() throws Exception {
        LocalDateTime searchTime = LocalDateTime.now();
        SearchHits searchHits = mock(SearchHits.class);
        when(searchHits.getHits()).thenReturn(new SearchHit[0]);
        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(searchHits);
        when(openSearchClient.search(any(), any())).thenReturn(searchResponse);

        List<ReminderRecord> result = openSearchService.findRemindersByTime(searchTime).get();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findRemindersByUser_FoundReminders() throws Exception {
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("user_id", testUserId);
        sourceMap.put("original_text", "Напомни завтра купить молоко");
        sourceMap.put("extracted_action", "купить молоко");
        sourceMap.put("scheduled_time", LocalDateTime.now().plusDays(1).toString());
        sourceMap.put("reminder_time", "утро");
        sourceMap.put("status", "SCHEDULED");
        sourceMap.put("notification_sent", false);
        sourceMap.put("created_at", LocalDateTime.now().toString());
        sourceMap.put("eventbridge_rule_name", "rule-123");

        SearchHit searchHit = mock(SearchHit.class);
        when(searchHit.getId()).thenReturn(testReminderId);
        when(searchHit.getSourceAsMap()).thenReturn(sourceMap);

        SearchHits searchHits = mock(SearchHits.class);
        when(searchHits.getHits()).thenReturn(new SearchHit[]{searchHit});

        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(searchHits);
        when(openSearchClient.search(any(), any())).thenReturn(searchResponse);

        List<ReminderRecord> result = openSearchService.findRemindersByUser(testUserId, 10).get();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUserId, result.get(0).userId());
    }

    @Test
    void getReminderById_Found() throws Exception {
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("user_id", testUserId);
        sourceMap.put("original_text", "Напомни завтра купить молоко");
        sourceMap.put("extracted_action", "купить молоко");
        sourceMap.put("scheduled_time", LocalDateTime.now().plusDays(1).toString());
        sourceMap.put("reminder_time", "утро");
        sourceMap.put("status", "SCHEDULED");
        sourceMap.put("notification_sent", false);
        sourceMap.put("created_at", LocalDateTime.now().toString());
        sourceMap.put("eventbridge_rule_name", "rule-123");

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getId()).thenReturn(testReminderId);
        when(getResponse.getSourceAsMap()).thenReturn(sourceMap);
        when(openSearchClient.get(any(), any())).thenReturn(getResponse);

        Optional<ReminderRecord> result = openSearchService.getReminderById(testReminderId).get();
        assertTrue(result.isPresent());
        assertEquals(testReminderId, result.get().reminderId());
    }

    @Test
    void getReminderById_NotFound() throws Exception {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);
        when(openSearchClient.get(any(), any())).thenReturn(getResponse);

        Optional<ReminderRecord> result = openSearchService.getReminderById("non-existing-id").get();
        assertTrue(result.isEmpty());
    }

    @Test
    void updateReminderStatus_Success() throws Exception {
        UpdateResponse updateResponse = mock(UpdateResponse.class);
        when(updateResponse.getResult()).thenReturn(UpdateResponse.Result.UPDATED);
        when(openSearchClient.update(any(), any())).thenReturn(updateResponse);

        boolean result = openSearchService.updateReminderStatus(
                testReminderId,
                ReminderRecord.ReminderStatus.COMPLETED,
                true
        ).get();

        assertTrue(result);
    }

    @Test
    void deleteReminder_Success() throws Exception {
        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.getResult()).thenReturn(DeleteResponse.Result.DELETED);
        when(openSearchClient.delete(any(), any())).thenReturn(deleteResponse);

        boolean result = openSearchService.deleteReminder(testReminderId).get();
        assertTrue(result);
    }

    @Test
    void searchTranscriptions_FoundResults() throws Exception {
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("original_audio_key", testAudioKey);
        sourceMap.put("transcribed_text", "тестовый текст транскрипции");
        sourceMap.put("confidence", 0.95);
        sourceMap.put("language", "ru-RU");
        sourceMap.put("duration_seconds", 30.5);
        sourceMap.put("completed_at", LocalDateTime.now().minusHours(1).toString());
        sourceMap.put("user_id", testUserId);

        SearchHit searchHit = mock(SearchHit.class);
        when(searchHit.getId()).thenReturn(testTranscriptionId);
        when(searchHit.getSourceAsMap()).thenReturn(sourceMap);

        SearchHits searchHits = mock(SearchHits.class);
        when(searchHits.getHits()).thenReturn(new SearchHit[]{searchHit});

        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(searchHits);
        when(openSearchClient.search(any(), any())).thenReturn(searchResponse);

        List<TranscriptionResult> result = openSearchService
                .searchTranscriptions("тестовый", testUserId, 10)
                .get();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testTranscriptionId, result.get(0).transcriptionId());
    }

    @Test
    void searchTranscriptions_WithoutUserId() throws Exception {
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("original_audio_key", testAudioKey);
        sourceMap.put("transcribed_text", "тестовый текст транскрипции");
        sourceMap.put("confidence", 0.95);
        sourceMap.put("language", "ru-RU");
        sourceMap.put("duration_seconds", 30.5);
        sourceMap.put("completed_at", LocalDateTime.now().minusHours(1).toString());
        sourceMap.put("user_id", testUserId);

        SearchHit searchHit = mock(SearchHit.class);
        when(searchHit.getId()).thenReturn(testTranscriptionId);
        when(searchHit.getSourceAsMap()).thenReturn(sourceMap);

        SearchHits searchHits = mock(SearchHits.class);
        when(searchHits.getHits()).thenReturn(new SearchHit[]{searchHit});

        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(searchHits);
        when(openSearchClient.search(any(), any())).thenReturn(searchResponse);

        List<TranscriptionResult> result = openSearchService
                .searchTranscriptions("тестовый", null, 10)
                .get();

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getReminderStats_Success() throws Exception {
        SearchHits searchHits = mock(SearchHits.class);
        TotalHits totalHits = new TotalHits(10L, TotalHits.Relation.EQUAL_TO);
        when(searchHits.getTotalHits()).thenReturn(totalHits);

        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.getHits()).thenReturn(searchHits);
        when(mockResponse.getAggregations()).thenReturn(mock(Aggregations.class));

        int statusCount = ReminderRecord.ReminderStatus.values().length;
        List<SearchResponse> responses = new ArrayList<>();
        responses.add(mockResponse);
        for (int i = 0; i < statusCount; i++) {
            responses.add(mockResponse);
        }

        when(openSearchClient.search(any(), any()))
                .thenReturn(responses.get(0), responses.subList(1, responses.size()).toArray(new SearchResponse[0]));

        Map<String, Object> result = openSearchService.getReminderStats(testUserId).get();
        assertNotNull(result);
        assertTrue(result.containsKey("total"));
        verify(openSearchClient, times(1 + statusCount)).search(any(), any());
    }

    @Test
    void updateReminderEventBridgeRule_Success() throws Exception {
        UpdateResponse updateResponse = mock(UpdateResponse.class);
        when(updateResponse.getResult()).thenReturn(UpdateResponse.Result.UPDATED);
        when(openSearchClient.update(any(), any())).thenReturn(updateResponse);

        boolean result = openSearchService
                .updateReminderEventBridgeRule(testReminderId, "new-rule-456")
                .get();

        assertTrue(result);
    }

    @Test
    void cleanupIndices_Success() throws Exception {
        when(indicesClient.exists(any(GetIndexRequest.class), any(RequestOptions.class)))
                .thenReturn(true);

        openSearchService.cleanupIndices().get();
        verify(indicesClient, times(2)).delete(any(DeleteIndexRequest.class), any(RequestOptions.class));
    }

    @Test
    void cleanupIndices_IndicesNotExist() throws Exception {
        when(indicesClient.exists(any(), any())).thenReturn(false);
        openSearchService.cleanupIndices().get();
        verify(indicesClient, never()).delete(any(), any());
    }

    @Test
    void cleanupIndices_IOException_LogsError() throws Exception {
        when(indicesClient.exists(any(), any())).thenReturn(true);
        when(indicesClient.delete(any(), any())).thenThrow(new IOException("Delete failed"));
        assertDoesNotThrow(() -> openSearchService.cleanupIndices().get());
        verify(indicesClient, atLeastOnce()).delete(any(), any());
    }
}