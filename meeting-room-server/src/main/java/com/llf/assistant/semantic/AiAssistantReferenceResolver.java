package com.llf.assistant.semantic;

import com.llf.assistant.AiAssistantSessionStore;
import org.springframework.stereotype.Component;

@Component
public class AiAssistantReferenceResolver {

    public void resolve(AiAssistantIntentParseResult parseResult, AiAssistantSessionStore.Session session) {
        if (parseResult == null || session == null) {
            return;
        }
        String text = parseResult.getNormalizedText();
        AiAssistantIntentFields fields = parseResult.getFields();
        if (text == null || fields == null) {
            return;
        }
        if (fields.getReservationId() == null
                && session.getLastReservationId() != null
                && containsAny(text, "那个会", "那场会", "这场会", "上次那个会")
                && parseResult.getActionType().startsWith("reservations.")) {
            fields.setReservationId(session.getLastReservationId());
        }
        if (fields.getRoomId() == null
                && session.getLastRoomId() != null
                && containsAny(text, "那个会议室", "这间会议室", "这间房", "这个会议室")
                && ("rooms.detail".equals(parseResult.getActionType()) || "reservations.create".equals(parseResult.getActionType()))) {
            fields.setRoomId(session.getLastRoomId());
        }
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
