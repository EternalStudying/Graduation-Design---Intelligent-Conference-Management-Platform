package com.llf.vo.assistant;

import lombok.Data;

import java.util.List;

@Data
public class AiChatSessionVO {
    private String sessionId;
    private String welcome;
    private List<String> suggestions;
}
