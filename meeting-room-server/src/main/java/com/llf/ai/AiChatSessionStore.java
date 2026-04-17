package com.llf.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiChatSessionStore {

    private static final int MAX_HISTORY_SIZE = 6;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Session create(Long userId) {
        Session session = new Session("assistant-" + UUID.randomUUID().toString().replace("-", ""), userId);
        sessions.put(session.getSessionId(), session);
        return session;
    }

    public Session getOrCreate(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return create(userId);
        }
        Session existing = sessions.get(sessionId.trim());
        if (existing == null || !Objects.equals(existing.getUserId(), userId)) {
            return create(userId);
        }
        existing.touch();
        return existing;
    }

    public void addUserMessage(Session session, String content) {
        addMessage(session, "user", content);
    }

    public void addAssistantMessage(Session session, String content) {
        addMessage(session, "assistant", content);
    }

    public List<String> recentMessages(Session session) {
        List<String> result = new ArrayList<>();
        for (Message message : session.getHistory()) {
            result.add(message.role() + ": " + message.content());
        }
        return result;
    }

    private void addMessage(Session session, String role, String content) {
        if (session == null || content == null || content.isBlank()) {
            return;
        }
        session.getHistory().addLast(new Message(role, content.trim()));
        while (session.getHistory().size() > MAX_HISTORY_SIZE) {
            session.getHistory().removeFirst();
        }
        session.touch();
    }

    @Getter
    public static class Session {
        private final String sessionId;
        private final Long userId;
        private final Deque<Message> history = new ArrayDeque<>();
        private LocalDateTime updatedAt;

        @Setter
        private RoomQueryContext lastRoomQuery;

        public Session(String sessionId, Long userId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.updatedAt = LocalDateTime.now();
        }

        public void touch() {
            this.updatedAt = LocalDateTime.now();
        }
    }

    public record Message(String role, String content) {
    }

    public record RoomQueryContext(LocalDateTime start, LocalDateTime end, Integer attendees) {
    }
}
