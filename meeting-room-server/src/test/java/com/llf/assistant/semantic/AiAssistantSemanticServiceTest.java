package com.llf.assistant.semantic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llf.assistant.AiAssistantActionRegistry;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAssistantSemanticServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseTemplateCases_shouldMatchExpectedIntent() throws Exception {
        AiAssistantSemanticService semanticService = createSemanticService();
        List<ColloquialCase> cases = loadCases();

        for (ColloquialCase item : cases) {
            AiAssistantIntentParseResult result = semanticService.parse(item.userText(), null);

            assertNotNull(result, item.id());
            assertEquals(item.expectedActionType(), result.getActionType(), item.id());
            assertEquals(item.expectedNeedClarification(), result.isNeedClarification(), item.id());
            assertNotNull(result.getNormalizedText(), item.id());
            assertTrue(result.getConfidence() >= 0, item.id());
            assertExpectedFields(item, result);
        }
    }

    @Test
    void parseCoreCases_shouldFallbackWhenLlmUnavailable() {
        AiAssistantSemanticService semanticService = createSemanticService();

        AiAssistantIntentParseResult tomorrowMeeting = semanticService.parse("我明天有会吗", null);
        assertEquals("reservations.list", tomorrowMeeting.getActionType());
        assertEquals("tomorrow", tomorrowMeeting.getFields().getTimeRangeLabel());

        AiAssistantIntentParseResult tomorrowReservation = semanticService.parse("我明天有预约吗", null);
        assertEquals("reservations.list", tomorrowReservation.getActionType());
        assertEquals("tomorrow", tomorrowReservation.getFields().getTimeRangeLabel());

        AiAssistantIntentParseResult tomorrowAnyReservation = semanticService.parse("我明天有没有预约", null);
        assertEquals("reservations.list", tomorrowAnyReservation.getActionType());
        assertEquals("tomorrow", tomorrowAnyReservation.getFields().getTimeRangeLabel());

        AiAssistantIntentParseResult cancelMeeting = semanticService.parse("帮我把明天下午那个会撤了", null);
        assertEquals("reservations.cancel", cancelMeeting.getActionType());
        assertTrue(cancelMeeting.isNeedClarification());

        AiAssistantIntentParseResult createMeeting = semanticService.parse("给我订个10个人的会议室", null);
        assertEquals("reservations.create", createMeeting.getActionType());
        assertEquals(10, createMeeting.getFields().getAttendees());

        AiAssistantIntentParseResult weekendMeeting = semanticService.parse("这周末我有会吗", null);
        assertEquals("reservations.list", weekendMeeting.getActionType());
        assertEquals("this_weekend", weekendMeeting.getFields().toMap().get("timeRangeLabel"));

        AiAssistantIntentParseResult moveMeeting = semanticService.parse("把我明天下午那个会往后挪半小时", null);
        assertEquals("reservations.update", moveMeeting.getActionType());
        assertEquals("delay_later", moveMeeting.getFields().toMap().get("mutationHint"));
        assertEquals(30, moveMeeting.getFields().toMap().get("timeShiftMinutes"));

        AiAssistantIntentParseResult deviceMeeting = semanticService.parse("找个带白板和投影的会议室", null);
        assertEquals("rooms.search", deviceMeeting.getActionType());
        assertEquals("投影、白板", deviceMeeting.getFields().toMap().get("deviceRequirements"));
    }

    @Test
    void parseInvalidLlmJson_shouldFallbackToRuleResult() {
        AiAssistantActionRegistry actionRegistry = new AiAssistantActionRegistry(List.of());
        AiAssistantIntentSchemaValidator schemaValidator = new AiAssistantIntentSchemaValidator();
        AiAssistantSemanticService semanticService = new AiAssistantSemanticService(
                new AiAssistantTextNormalizer(),
                new AiAssistantTimeResolver(),
                new AiAssistantIntentParser(actionRegistry, schemaValidator, request -> "{\"foo\":\"bar\"}"),
                schemaValidator,
                new AiAssistantReferenceResolver()
        );

        AiAssistantIntentParseResult result = semanticService.parse("我明天有会吗", null);

        assertEquals("reservations.list", result.getActionType());
        assertEquals("tomorrow", result.getFields().getTimeRangeLabel());
    }

    private AiAssistantSemanticService createSemanticService() {
        AiAssistantActionRegistry actionRegistry = new AiAssistantActionRegistry(List.of());
        AiAssistantIntentSchemaValidator schemaValidator = new AiAssistantIntentSchemaValidator();
        return new AiAssistantSemanticService(
                new AiAssistantTextNormalizer(),
                new AiAssistantTimeResolver(),
                new AiAssistantIntentParser(actionRegistry, schemaValidator, request -> null),
                schemaValidator,
                new AiAssistantReferenceResolver()
        );
    }

    private List<ColloquialCase> loadCases() throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("ai/assistant-colloquial-cases.template.json")) {
            assertNotNull(inputStream);
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    private void assertExpectedFields(ColloquialCase item, AiAssistantIntentParseResult result) {
        Map<String, Object> expectedFields = item.expectedFields();
        if (expectedFields == null || expectedFields.isEmpty()) {
            return;
        }
        Map<String, Object> actualFields = result.getFields().toMap();
        for (Map.Entry<String, Object> entry : expectedFields.entrySet()) {
            assertEquals(entry.getValue(), actualFields.get(entry.getKey()), item.id() + ":" + entry.getKey());
        }
    }

    private record ColloquialCase(String id,
                                  String userText,
                                  String expectedActionType,
                                  boolean expectedNeedClarification,
                                  Map<String, Object> expectedFields,
                                  String notes) {
    }
}
