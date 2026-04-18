package com.llf.assistant;

import com.llf.vo.assistant.AiAssistantSummaryItemVO;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class AiAssistantExecutionResult {
    private final String assistantText;
    private final String status;
    private final String title;
    private final List<AiAssistantSummaryItemVO> summaryItems;
    private final String deepLink;

    private AiAssistantExecutionResult(String assistantText,
                                       String status,
                                       String title,
                                       List<AiAssistantSummaryItemVO> summaryItems,
                                       String deepLink) {
        this.assistantText = assistantText;
        this.status = status;
        this.title = title;
        this.summaryItems = summaryItems == null ? List.of() : new ArrayList<>(summaryItems);
        this.deepLink = deepLink;
    }

    public static AiAssistantExecutionResult success(String assistantText,
                                                     String title,
                                                     List<AiAssistantSummaryItemVO> summaryItems,
                                                     String deepLink) {
        return new AiAssistantExecutionResult(assistantText, "success", title, summaryItems, deepLink);
    }

    public static AiAssistantExecutionResult error(String assistantText,
                                                   String title,
                                                   List<AiAssistantSummaryItemVO> summaryItems) {
        return new AiAssistantExecutionResult(assistantText, "error", title, summaryItems, null);
    }
}
