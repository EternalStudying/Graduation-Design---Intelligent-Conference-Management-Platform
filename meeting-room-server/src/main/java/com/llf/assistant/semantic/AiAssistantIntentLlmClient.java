package com.llf.assistant.semantic;

@FunctionalInterface
public interface AiAssistantIntentLlmClient {
    String parseIntentJson(AiAssistantIntentLlmRequest request);
}
