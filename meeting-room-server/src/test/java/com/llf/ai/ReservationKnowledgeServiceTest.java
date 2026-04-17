package com.llf.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationKnowledgeServiceTest {

    @Test
    void search_shouldReturnIdentityKnowledgeForWhoAreYouQuestion() {
        ReservationKnowledgeService service = new ReservationKnowledgeService(new ObjectMapper());

        List<String> snippets = service.search("你是谁");

        assertFalse(snippets.isEmpty());
        assertTrue(snippets.stream().anyMatch(item -> item.contains("会议室预约助手")));
    }

    @Test
    void search_shouldReturnDefaultKnowledgeWhenNoKeywordMatches() {
        ReservationKnowledgeService service = new ReservationKnowledgeService(new ObjectMapper());

        List<String> snippets = service.search("随便问一句");

        assertFalse(snippets.isEmpty());
        assertTrue(snippets.stream().anyMatch(item -> item.contains("会议室和预约相关问题")));
    }
}
