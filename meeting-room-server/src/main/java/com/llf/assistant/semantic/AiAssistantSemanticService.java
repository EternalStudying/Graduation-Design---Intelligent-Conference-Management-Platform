package com.llf.assistant.semantic;

import com.llf.assistant.AiAssistantSessionStore;
import com.llf.vo.assistant.AiAssistantTurnVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiAssistantSemanticService {

    private final AiAssistantTextNormalizer textNormalizer;
    private final AiAssistantTimeResolver timeResolver;
    private final AiAssistantIntentParser intentParser;
    private final AiAssistantIntentSchemaValidator schemaValidator;
    private final AiAssistantReferenceResolver referenceResolver;

    public AiAssistantSemanticService(AiAssistantTextNormalizer textNormalizer,
                                      AiAssistantTimeResolver timeResolver,
                                      AiAssistantIntentParser intentParser,
                                      AiAssistantIntentSchemaValidator schemaValidator,
                                      AiAssistantReferenceResolver referenceResolver) {
        this.textNormalizer = textNormalizer;
        this.timeResolver = timeResolver;
        this.intentParser = intentParser;
        this.schemaValidator = schemaValidator;
        this.referenceResolver = referenceResolver;
    }

    public AiAssistantIntentParseResult parse(String userText, AiAssistantSessionStore.Session session) {
        String normalizedText = textNormalizer.normalize(userText);
        AiAssistantIntentParseResult ruleResult = intentParser.parseByRules(userText, normalizedText);
        timeResolver.resolve(userText, normalizedText, ruleResult.getFields());
        referenceResolver.resolve(ruleResult, session);
        if (schemaValidator.isValid(ruleResult) && ruleResult.getConfidence() >= 0.85D) {
            return ruleResult;
        }

        AiAssistantIntentParseResult llmResult = intentParser.parseWithSchema(userText, normalizedText, session);
        if (llmResult != null) {
            timeResolver.resolve(userText, llmResult.getNormalizedText(), llmResult.getFields());
            referenceResolver.resolve(llmResult, session);
            llmResult.getFields().mergeFrom(ruleResult.getFields());
            if (schemaValidator.isValid(llmResult)) {
                return llmResult;
            }
        }
        return ruleResult;
    }

    public Map<String, Object> toDraftMap(AiAssistantIntentParseResult parseResult) {
        return parseResult == null || parseResult.getFields() == null ? Map.of() : parseResult.getFields().toMap();
    }

    public AiAssistantTurnVO toClarificationTurn(String sessionId, AiAssistantIntentParseResult parseResult, List<String> suggestions) {
        AiAssistantTurnVO turn = new AiAssistantTurnVO();
        turn.setSessionId(sessionId);
        turn.setStage("collect");
        turn.setAssistantText(parseResult.getClarificationReason() == null ? "请再说具体一点。" : parseResult.getClarificationReason());
        turn.setSuggestions(suggestions == null ? List.of() : suggestions);
        return turn;
    }
}
