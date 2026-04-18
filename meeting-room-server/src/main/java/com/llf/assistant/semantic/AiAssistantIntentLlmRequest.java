package com.llf.assistant.semantic;

import java.util.List;

public record AiAssistantIntentLlmRequest(String originalText,
                                          String normalizedText,
                                          String currentActionType,
                                          List<String> recentMessages,
                                          String schemaText) {
}
