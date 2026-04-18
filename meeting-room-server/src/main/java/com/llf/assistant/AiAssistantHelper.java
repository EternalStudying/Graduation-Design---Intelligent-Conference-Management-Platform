package com.llf.assistant;

import com.llf.vo.assistant.AiAssistantFieldOptionVO;
import com.llf.vo.assistant.AiAssistantMissingFieldVO;
import com.llf.vo.assistant.AiAssistantSummaryItemVO;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AiAssistantHelper {

    private AiAssistantHelper() {
    }

    public static String stringValue(Map<String, Object> values, String key) {
        if (values == null || key == null) {
            return null;
        }
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isBlank() ? null : result;
    }

    public static Long longValue(Map<String, Object> values, String key) {
        String text = stringValue(values, key);
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer integerValue(Map<String, Object> values, String key) {
        String text = stringValue(values, key);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static List<Long> longListValue(Map<String, Object> values, String key) {
        if (values == null || key == null) {
            return List.of();
        }
        Object value = values.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> listValue) {
            return listValue.stream()
                    .map(AiAssistantHelper::toLong)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        Long singleValue = toLong(value);
        return singleValue == null ? List.of() : List.of(singleValue);
    }

    public static AiAssistantMissingFieldVO field(String key,
                                                  String label,
                                                  String inputType,
                                                  boolean required,
                                                  String placeholder,
                                                  Object value,
                                                  List<AiAssistantFieldOptionVO> options) {
        AiAssistantMissingFieldVO field = new AiAssistantMissingFieldVO();
        field.setKey(key);
        field.setLabel(label);
        field.setInputType(inputType);
        field.setRequired(required);
        field.setPlaceholder(placeholder);
        field.setValue(value);
        if (options != null) {
            field.setOptions(options);
        }
        return field;
    }

    public static AiAssistantFieldOptionVO option(String label, Object value) {
        return new AiAssistantFieldOptionVO(label, value);
    }

    public static AiAssistantSummaryItemVO summary(String label, String value) {
        return new AiAssistantSummaryItemVO(label, value);
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
