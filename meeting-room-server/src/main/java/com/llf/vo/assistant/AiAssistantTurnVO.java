package com.llf.vo.assistant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiAssistantTurnVO {
    private String sessionId;
    private String stage;
    private String assistantText;
    private List<String> suggestions = new ArrayList<>();
    private List<AiAssistantMissingFieldVO> missingFields = new ArrayList<>();
    private AiAssistantPendingActionVO pendingAction;
    private AiAssistantResultVO result;
}
