package com.llf.vo.assistant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiAssistantPendingActionVO {
    private String executionId;
    private String actionType;
    private String title;
    private List<AiAssistantSummaryItemVO> summaryItems = new ArrayList<>();
    private Boolean confirmRequired;
}
