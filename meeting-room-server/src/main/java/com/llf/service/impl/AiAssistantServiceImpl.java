package com.llf.service.impl;

import com.llf.assistant.AiAssistantActionHandler;
import com.llf.assistant.AiAssistantActionPlan;
import com.llf.assistant.AiAssistantActionRegistry;
import com.llf.assistant.AiAssistantExecutionResult;
import com.llf.assistant.AiAssistantSessionStore;
import com.llf.assistant.semantic.AiAssistantIntentParseResult;
import com.llf.assistant.semantic.AiAssistantSemanticService;
import com.llf.auth.AuthUser;
import com.llf.dto.assistant.AiAssistantMessageRequestDTO;
import com.llf.result.BizException;
import com.llf.service.AiAssistantService;
import com.llf.vo.assistant.AiAssistantPendingActionVO;
import com.llf.vo.assistant.AiAssistantResultVO;
import com.llf.vo.assistant.AiAssistantTurnVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final String INVALID_EXECUTION_MESSAGE = "这次待执行动作已经失效，请重新描述你的需求。";
    private static final String OUT_OF_SCOPE_MESSAGE = "我当前只支持普通用户侧的概览、会议室、日历和预约相关任务。你可以直接告诉我要查询什么，或者让我代你创建、修改、取消预约。";
    private static final List<String> DEFAULT_SUGGESTIONS = List.of(
            "帮我创建一个预约",
            "取消我明天下午的预约",
            "查看我本周的预约",
            "今天下午有哪些空闲会议室"
    );

    private final AiAssistantSessionStore sessionStore;
    private final AiAssistantActionRegistry actionRegistry;
    private final AiAssistantSemanticService semanticService;

    public AiAssistantServiceImpl(AiAssistantSessionStore sessionStore,
                                  AiAssistantActionRegistry actionRegistry,
                                  AiAssistantSemanticService semanticService) {
        this.sessionStore = sessionStore;
        this.actionRegistry = actionRegistry;
        this.semanticService = semanticService;
    }

    @Override
    public AiAssistantTurnVO createSession(AuthUser currentUser) {
        Long userId = requireUserId(currentUser);
        AiAssistantSessionStore.Session session = sessionStore.create(userId);
        return baseTurn(session.getSessionId(), "reply",
                "我是你的任务助手，可以帮你查询概览、会议室、日历和我的预约，也可以代你完成预约相关操作。",
                DEFAULT_SUGGESTIONS);
    }

    @Override
    public AiAssistantTurnVO message(AuthUser currentUser, AiAssistantMessageRequestDTO dto) {
        Long userId = requireUserId(currentUser);
        AiAssistantSessionStore.Session session = sessionStore.getOrCreate(userId, dto == null ? null : dto.getSessionId());
        String message = dto == null || dto.getMessage() == null ? null : dto.getMessage().trim();
        Map<String, Object> fieldValues = dto == null ? null : dto.getFieldValues();

        if (message != null && !message.isBlank()) {
            sessionStore.rememberMessage(session, "user", message);
            AiAssistantIntentParseResult parseResult = semanticService.parse(message, session);
            if (!"unknown".equals(parseResult.getActionType())) {
                clearPendingExecution(session);
                sessionStore.resetForNewAction(session, parseResult.getActionType());
                sessionStore.mergeDraft(session, semanticService.toDraftMap(parseResult));
                message = parseResult.getNormalizedText();
            } else if (session.getCurrentActionType() != null) {
                sessionStore.mergeDraft(session, semanticService.toDraftMap(parseResult));
                message = parseResult.getNormalizedText();
            } else if (parseResult.isNeedClarification()) {
                sessionStore.clearProgress(session);
                AiAssistantTurnVO turn = semanticService.toClarificationTurn(session.getSessionId(), parseResult, DEFAULT_SUGGESTIONS);
                sessionStore.rememberMessage(session, "assistant", turn.getAssistantText());
                return turn;
            } else if (session.getCurrentActionType() == null) {
                return baseTurn(session.getSessionId(), "reply", OUT_OF_SCOPE_MESSAGE, DEFAULT_SUGGESTIONS);
            }
        }

        if (fieldValues != null && !fieldValues.isEmpty()) {
            if (session.getCurrentActionType() == null) {
                return errorTurn(session.getSessionId(), "当前没有待补充的任务，请先告诉我你的需求。");
            }
            sessionStore.mergeDraft(session, fieldValues);
        }

        if ((message == null || message.isBlank()) && (fieldValues == null || fieldValues.isEmpty())) {
            return errorTurn(session.getSessionId(), "请提供 message 或 fieldValues。");
        }

        String actionType = session.getCurrentActionType();
        AiAssistantActionHandler handler = actionRegistry.get(actionType);
        if (handler == null) {
            return baseTurn(session.getSessionId(), "reply", OUT_OF_SCOPE_MESSAGE, DEFAULT_SUGGESTIONS);
        }

        AiAssistantActionPlan plan = handler.process(actionType, currentUser, session, message);
        session.setStage(plan.getStage());
        return toTurn(session, actionType, plan);
    }

    @Override
    public AiAssistantTurnVO confirm(AuthUser currentUser, String executionId) {
        Long userId = requireUserId(currentUser);
        AiAssistantSessionStore.PendingExecution execution = sessionStore.findValidExecution(userId, executionId);
        if (execution == null) {
            return invalidExecutionTurn();
        }

        AiAssistantActionHandler handler = actionRegistry.get(execution.getActionType());
        if (handler == null) {
            sessionStore.removeExecution(executionId);
            return invalidExecutionTurn();
        }

        AiAssistantSessionStore.Session session = sessionStore.getOrCreate(userId, execution.getSessionId());
        try {
            AiAssistantExecutionResult executionResult = handler.execute(execution.getActionType(), currentUser, execution.getParams());
            audit(userId, execution, executionResult.getStatus(), executionResult.getTitle());
            sessionStore.removeExecution(executionId);
            sessionStore.clearProgress(session);
            return resultTurn(session.getSessionId(), executionResult);
        } catch (BizException e) {
            audit(userId, execution, "error", e.getMessage());
            sessionStore.removeExecution(executionId);
            sessionStore.clearProgress(session);
            return errorTurn(session.getSessionId(), e.getMessage());
        }
    }

    @Override
    public AiAssistantTurnVO cancel(AuthUser currentUser, String executionId) {
        Long userId = requireUserId(currentUser);
        AiAssistantSessionStore.PendingExecution execution = sessionStore.findValidExecution(userId, executionId);
        if (execution == null) {
            return invalidExecutionTurn();
        }

        AiAssistantSessionStore.Session session = sessionStore.getOrCreate(userId, execution.getSessionId());
        sessionStore.removeExecution(executionId);
        sessionStore.clearProgress(session);

        AiAssistantResultVO result = new AiAssistantResultVO();
        result.setStatus("cancelled");
        result.setTitle("已取消本次操作");
        result.setSummaryItems(List.of(new com.llf.vo.assistant.AiAssistantSummaryItemVO("动作", execution.getTitle())));

        AiAssistantTurnVO turn = baseTurn(session.getSessionId(), "result", "本次操作已取消。", DEFAULT_SUGGESTIONS);
        turn.setResult(result);
        return turn;
    }

    private AiAssistantTurnVO toTurn(AiAssistantSessionStore.Session session, String actionType, AiAssistantActionPlan plan) {
        if ("confirm".equals(plan.getStage())) {
            AiAssistantSessionStore.PendingExecution execution = sessionStore.saveExecution(
                    session.getUserId(),
                    session.getSessionId(),
                    actionType,
                    plan.getParams(),
                    plan.getTitle(),
                    plan.getSummaryItems(),
                    true
            );
            AiAssistantPendingActionVO pendingAction = new AiAssistantPendingActionVO();
            pendingAction.setExecutionId(execution.getExecutionId());
            pendingAction.setActionType(actionType);
            pendingAction.setTitle(plan.getTitle());
            pendingAction.setSummaryItems(plan.getSummaryItems());
            pendingAction.setConfirmRequired(Boolean.TRUE);

            AiAssistantTurnVO turn = baseTurn(session.getSessionId(), "confirm", plan.getAssistantText(), plan.getSuggestions());
            turn.setPendingAction(pendingAction);
            return turn;
        }

        AiAssistantTurnVO turn = baseTurn(session.getSessionId(), plan.getStage(), plan.getAssistantText(), plan.getSuggestions());
        turn.setMissingFields(plan.getMissingFields());
        turn.setResult(plan.getResult());
        sessionStore.rememberMessage(session, "assistant", plan.getAssistantText());
        return turn;
    }

    private AiAssistantTurnVO resultTurn(String sessionId, AiAssistantExecutionResult executionResult) {
        AiAssistantTurnVO turn = baseTurn(sessionId, "result", executionResult.getAssistantText(), DEFAULT_SUGGESTIONS);
        AiAssistantResultVO result = new AiAssistantResultVO();
        result.setStatus(executionResult.getStatus());
        result.setTitle(executionResult.getTitle());
        result.setSummaryItems(executionResult.getSummaryItems());
        result.setDeepLink(executionResult.getDeepLink());
        turn.setResult(result);
        return turn;
    }

    private AiAssistantTurnVO invalidExecutionTurn() {
        return errorTurn("asst-invalid", INVALID_EXECUTION_MESSAGE);
    }

    private AiAssistantTurnVO errorTurn(String sessionId, String assistantText) {
        AiAssistantResultVO result = new AiAssistantResultVO();
        result.setStatus("error");
        result.setTitle("动作执行失败");
        return errorTurn(sessionId, assistantText, result);
    }

    private AiAssistantTurnVO errorTurn(String sessionId, String assistantText, AiAssistantResultVO result) {
        AiAssistantTurnVO turn = baseTurn(sessionId, "error", assistantText, DEFAULT_SUGGESTIONS);
        turn.setResult(result);
        return turn;
    }

    private AiAssistantTurnVO baseTurn(String sessionId, String stage, String assistantText, List<String> suggestions) {
        AiAssistantTurnVO turn = new AiAssistantTurnVO();
        turn.setSessionId(sessionId);
        turn.setStage(stage);
        turn.setAssistantText(assistantText);
        turn.setSuggestions(suggestions == null ? List.of() : suggestions);
        return turn;
    }

    private void clearPendingExecution(AiAssistantSessionStore.Session session) {
        if (session.getPendingExecutionId() != null) {
            sessionStore.removeExecution(session.getPendingExecutionId());
        }
    }

    private Long requireUserId(AuthUser currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new BizException(401, "not logged in");
        }
        return currentUser.getId();
    }

    private void audit(Long userId, AiAssistantSessionStore.PendingExecution execution, String result, String resultTitle) {
        log.info("ai_assistant_audit userId={} sessionId={} executionId={} actionType={} params={} result={} createdAt={} title={}",
                userId,
                execution.getSessionId(),
                execution.getExecutionId(),
                execution.getActionType(),
                execution.getParams(),
                result,
                LocalDateTime.now(),
                resultTitle);
    }
}
