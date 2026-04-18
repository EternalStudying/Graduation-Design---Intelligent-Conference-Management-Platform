package com.llf.assistant.semantic;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiAssistantIntentParseResult {
    private String normalizedText;
    private String actionType;
    private double confidence;
    private boolean needClarification;
    private String clarificationReason;
    private AiAssistantIntentFields fields = new AiAssistantIntentFields();
    private List<AiAssistantIntentCandidate> candidates = new ArrayList<>();

    public static AiAssistantIntentParseResult unknown(String normalizedText, String clarificationReason) {
        AiAssistantIntentParseResult result = new AiAssistantIntentParseResult();
        result.setNormalizedText(normalizedText);
        result.setActionType("unknown");
        result.setConfidence(0.3D);
        result.setNeedClarification(true);
        result.setClarificationReason(clarificationReason);
        return result;
    }
}
