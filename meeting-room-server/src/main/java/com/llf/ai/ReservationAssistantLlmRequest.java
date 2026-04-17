package com.llf.ai;

import java.util.List;

public record ReservationAssistantLlmRequest(String question,
                                             String facts,
                                             List<String> knowledgeSnippets) {
}
