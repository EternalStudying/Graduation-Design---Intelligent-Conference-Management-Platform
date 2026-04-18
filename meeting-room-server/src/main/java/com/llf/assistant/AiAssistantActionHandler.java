package com.llf.assistant;

import com.llf.auth.AuthUser;

import java.util.List;
import java.util.Map;

public interface AiAssistantActionHandler {

    List<String> supportedActionTypes();

    AiAssistantActionPlan process(String actionType, AuthUser currentUser, AiAssistantSessionStore.Session session, String message);

    AiAssistantExecutionResult execute(String actionType, AuthUser currentUser, Map<String, Object> params);
}
