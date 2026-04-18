package com.llf.assistant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiAssistantSessionStore {

    private static final Duration EXECUTION_TTL = Duration.ofMinutes(30);
    private static final int MAX_HISTORY_SIZE = 6;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, PendingExecution> executions = new ConcurrentHashMap<>();

    public Session create(Long userId) {
        Session session = new Session("asst-" + UUID.randomUUID().toString().replace("-", ""), userId);
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

    public void mergeDraft(Session session, Map<String, Object> fieldValues) {
        if (session == null || fieldValues == null || fieldValues.isEmpty()) {
            return;
        }
        fieldValues.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                return;
            }
            if (value instanceof String stringValue && stringValue.isBlank()) {
                return;
            }
            session.getDraft().put(key, value);
        });
        session.touch();
    }

    public void rememberMessage(Session session, String role, String content) {
        if (session == null || content == null || content.isBlank()) {
            return;
        }
        session.getRecentMessages().addLast(role + ": " + content.trim());
        while (session.getRecentMessages().size() > MAX_HISTORY_SIZE) {
            session.getRecentMessages().removeFirst();
        }
        session.touch();
    }

    public PendingExecution saveExecution(Long userId,
                                          String sessionId,
                                          String actionType,
                                          Map<String, Object> params,
                                          String title,
                                          List<com.llf.vo.assistant.AiAssistantSummaryItemVO> summaryItems,
                                          boolean confirmRequired) {
        PendingExecution execution = new PendingExecution(
                "exec-" + UUID.randomUUID().toString().replace("-", ""),
                userId,
                sessionId,
                actionType,
                params == null ? Map.of() : new LinkedHashMap<>(params),
                title,
                summaryItems == null ? List.of() : new ArrayList<>(summaryItems),
                confirmRequired
        );
        executions.put(execution.getExecutionId(), execution);
        Session session = sessions.get(sessionId);
        if (session != null) {
            session.setPendingExecutionId(execution.getExecutionId());
            session.touch();
        }
        return execution;
    }

    public PendingExecution findValidExecution(Long userId, String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return null;
        }
        PendingExecution execution = executions.get(executionId.trim());
        if (execution == null) {
            return null;
        }
        if (!Objects.equals(execution.getUserId(), userId)) {
            return null;
        }
        if (execution.getCreatedAt().plus(EXECUTION_TTL).isBefore(LocalDateTime.now())) {
            executions.remove(execution.getExecutionId());
            return null;
        }
        return execution;
    }

    public void removeExecution(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return;
        }
        PendingExecution execution = executions.remove(executionId.trim());
        if (execution == null) {
            return;
        }
        Session session = sessions.get(execution.getSessionId());
        if (session != null && Objects.equals(session.getPendingExecutionId(), execution.getExecutionId())) {
            session.setPendingExecutionId(null);
            session.touch();
        }
    }

    public void resetForNewAction(Session session, String actionType) {
        session.setCurrentActionType(actionType);
        session.setStage(actionType == null ? "reply" : "collect");
        session.setPendingExecutionId(null);
        session.getDraft().clear();
        session.touch();
    }

    public void rememberReservationContext(Session session, Long reservationId) {
        if (session == null || reservationId == null) {
            return;
        }
        session.setLastReservationId(reservationId);
        session.touch();
    }

    public void rememberRoomContext(Session session, Long roomId) {
        if (session == null || roomId == null) {
            return;
        }
        session.setLastRoomId(roomId);
        session.touch();
    }

    public void clearProgress(Session session) {
        if (session == null) {
            return;
        }
        session.setCurrentActionType(null);
        session.setPendingExecutionId(null);
        session.setStage("reply");
        session.getDraft().clear();
        session.touch();
    }

    @Getter
    public static class Session {
        private final String sessionId;
        private final Long userId;
        private final Map<String, Object> draft = new LinkedHashMap<>();
        private final Deque<String> recentMessages = new ArrayDeque<>();
        @Setter
        private String currentActionType;
        @Setter
        private String pendingExecutionId;
        @Setter
        private String stage = "reply";
        @Setter
        private Long lastReservationId;
        @Setter
        private Long lastRoomId;
        private LocalDateTime updatedAt;

        public Session(String sessionId, Long userId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.updatedAt = LocalDateTime.now();
        }

        public void touch() {
            this.updatedAt = LocalDateTime.now();
        }
    }

    @Getter
    public static class PendingExecution {
        private final String executionId;
        private final Long userId;
        private final String sessionId;
        private final String actionType;
        private final Map<String, Object> params;
        private final String title;
        private final List<com.llf.vo.assistant.AiAssistantSummaryItemVO> summaryItems;
        private final boolean confirmRequired;
        private final LocalDateTime createdAt;

        public PendingExecution(String executionId,
                                Long userId,
                                String sessionId,
                                String actionType,
                                Map<String, Object> params,
                                String title,
                                List<com.llf.vo.assistant.AiAssistantSummaryItemVO> summaryItems,
                                boolean confirmRequired) {
            this.executionId = executionId;
            this.userId = userId;
            this.sessionId = sessionId;
            this.actionType = actionType;
            this.params = params;
            this.title = title;
            this.summaryItems = summaryItems;
            this.confirmRequired = confirmRequired;
            this.createdAt = LocalDateTime.now();
        }
    }
}
