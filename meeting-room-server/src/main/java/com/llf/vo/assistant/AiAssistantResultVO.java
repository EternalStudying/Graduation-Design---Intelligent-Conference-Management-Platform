package com.llf.vo.assistant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiAssistantResultVO {
    private String status;
    private String title;
    private List<AiAssistantSummaryItemVO> summaryItems = new ArrayList<>();
    private String deepLink;
}
