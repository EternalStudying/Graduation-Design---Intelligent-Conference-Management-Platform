package com.llf.assistant;

import com.llf.vo.assistant.AiAssistantMissingFieldVO;
import com.llf.vo.assistant.AiAssistantResultVO;
import com.llf.vo.assistant.AiAssistantSummaryItemVO;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class AiAssistantActionPlan {
    private final String stage;
    private final String assistantText;
    private final List<String> suggestions;
    private final List<AiAssistantMissingFieldVO> missingFields;
    private final Map<String, Object> params;
    private final String title;
    private final List<AiAssistantSummaryItemVO> summaryItems;
    private final AiAssistantResultVO result;

    private AiAssistantActionPlan(String stage,
                                  String assistantText,
                                  List<String> suggestions,
                                  List<AiAssistantMissingFieldVO> missingFields,
                                  Map<String, Object> params,
                                  String title,
                                  List<AiAssistantSummaryItemVO> summaryItems,
                                  AiAssistantResultVO result) {
        this.stage = stage;
        this.assistantText = assistantText;
        this.suggestions = suggestions == null ? List.of() : new ArrayList<>(suggestions);
        this.missingFields = missingFields == null ? List.of() : new ArrayList<>(missingFields);
        this.params = params;
        this.title = title;
        this.summaryItems = summaryItems == null ? List.of() : new ArrayList<>(summaryItems);
        this.result = result;
    }

    public static AiAssistantActionPlan reply(String assistantText, List<String> suggestions) {
        return new AiAssistantActionPlan("reply", assistantText, suggestions, List.of(), null, null, List.of(), null);
    }

    public static AiAssistantActionPlan collect(String assistantText,
                                                List<String> suggestions,
                                                List<AiAssistantMissingFieldVO> missingFields) {
        return new AiAssistantActionPlan("collect", assistantText, suggestions, missingFields, null, null, List.of(), null);
    }

    public static AiAssistantActionPlan confirm(String assistantText,
                                                List<String> suggestions,
                                                String title,
                                                List<AiAssistantSummaryItemVO> summaryItems,
                                                Map<String, Object> params) {
        return new AiAssistantActionPlan("confirm", assistantText, suggestions, List.of(), params, title, summaryItems, null);
    }

    public static AiAssistantActionPlan error(String assistantText, List<String> suggestions, AiAssistantResultVO result) {
        return new AiAssistantActionPlan("error", assistantText, suggestions, List.of(), null, null, List.of(), result);
    }
}
