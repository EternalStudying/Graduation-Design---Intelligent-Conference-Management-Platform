package com.llf.dto.assistant;

import lombok.Data;

import java.util.Map;

@Data
public class AiAssistantMessageRequestDTO {
    private String sessionId;
    private String message;
    private Map<String, Object> fieldValues;
}
