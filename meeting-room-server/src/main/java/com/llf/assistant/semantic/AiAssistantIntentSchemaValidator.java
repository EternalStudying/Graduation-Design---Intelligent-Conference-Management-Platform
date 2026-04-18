package com.llf.assistant.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class AiAssistantIntentSchemaValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonNode schemaNode;
    private final String schemaText;

    public AiAssistantIntentSchemaValidator() {
        try (InputStream inputStream = new ClassPathResource("ai/assistant-intent.schema.json").getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            this.schemaText = new String(bytes);
            this.schemaNode = objectMapper.readTree(bytes);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load assistant intent schema", e);
        }
    }

    public String getSchemaText() {
        return schemaText;
    }

    public boolean isValid(AiAssistantIntentParseResult result) {
        return validateNode(objectMapper.valueToTree(result));
    }

    public boolean validateJson(String json) {
        try {
            return validateNode(objectMapper.readTree(json));
        } catch (IOException e) {
            return false;
        }
    }

    public boolean validateNode(JsonNode dataNode) {
        return validate(schemaNode, dataNode, "$", new ArrayList<>());
    }

    private boolean validate(JsonNode schema, JsonNode data, String path, List<String> errors) {
        if (!matchesType(schema.get("type"), data)) {
            errors.add(path + ":type");
            return false;
        }
        JsonNode enumNode = schema.get("enum");
        if (enumNode != null && !matchesEnum(enumNode, data)) {
            errors.add(path + ":enum");
            return false;
        }
        if (data != null && data.isNumber()) {
            JsonNode minimum = schema.get("minimum");
            if (minimum != null && data.doubleValue() < minimum.doubleValue()) {
                errors.add(path + ":minimum");
                return false;
            }
            JsonNode maximum = schema.get("maximum");
            if (maximum != null && data.doubleValue() > maximum.doubleValue()) {
                errors.add(path + ":maximum");
                return false;
            }
        }
        if (data != null && data.isObject()) {
            JsonNode required = schema.get("required");
            if (required != null) {
                for (JsonNode item : required) {
                    if (!data.has(item.asText())) {
                        errors.add(path + "." + item.asText() + ":required");
                        return false;
                    }
                }
            }
            JsonNode properties = schema.get("properties");
            if (properties != null) {
                Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode childSchema = properties.get(entry.getKey());
                    if (childSchema == null) {
                        JsonNode additionalProperties = schema.get("additionalProperties");
                        if (additionalProperties != null && !additionalProperties.asBoolean(true)) {
                            errors.add(path + "." + entry.getKey() + ":additionalProperties");
                            return false;
                        }
                        continue;
                    }
                    if (!validate(childSchema, entry.getValue(), path + "." + entry.getKey(), errors)) {
                        return false;
                    }
                }
            }
        }
        if (data != null && data.isArray()) {
            JsonNode items = schema.get("items");
            if (items != null) {
                for (int i = 0; i < data.size(); i++) {
                    if (!validate(items, data.get(i), path + "[" + i + "]", errors)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean matchesType(JsonNode typeNode, JsonNode data) {
        if (typeNode == null) {
            return true;
        }
        if (typeNode.isArray()) {
            for (JsonNode item : typeNode) {
                if (matchesSingleType(item.asText(), data)) {
                    return true;
                }
            }
            return false;
        }
        return matchesSingleType(typeNode.asText(), data);
    }

    private boolean matchesSingleType(String type, JsonNode data) {
        return switch (type) {
            case "object" -> data != null && data.isObject();
            case "array" -> data != null && data.isArray();
            case "string" -> data != null && data.isTextual();
            case "number" -> data != null && data.isNumber();
            case "integer" -> data != null && data.isIntegralNumber();
            case "boolean" -> data != null && data.isBoolean();
            case "null" -> data == null || data.isNull();
            default -> true;
        };
    }

    private boolean matchesEnum(JsonNode enumNode, JsonNode data) {
        for (JsonNode candidate : enumNode) {
            if (candidate.isNull() && (data == null || data.isNull())) {
                return true;
            }
            if (candidate.equals(data)) {
                return true;
            }
        }
        return false;
    }
}
