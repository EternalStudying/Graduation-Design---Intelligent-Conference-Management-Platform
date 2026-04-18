package com.llf.vo.assistant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiAssistantMissingFieldVO {
    private String key;
    private String label;
    private String inputType;
    private boolean required;
    private String placeholder;
    private List<AiAssistantFieldOptionVO> options = new ArrayList<>();
    private Object value;
}
