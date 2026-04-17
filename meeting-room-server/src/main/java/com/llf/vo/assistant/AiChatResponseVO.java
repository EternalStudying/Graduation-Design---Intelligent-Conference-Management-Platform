package com.llf.vo.assistant;

import lombok.Data;

import java.util.List;

@Data
public class AiChatResponseVO {
    private String sessionId;
    private String answer;
    private List<String> suggestions;
}
