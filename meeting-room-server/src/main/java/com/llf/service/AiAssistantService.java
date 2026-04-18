package com.llf.service;

import com.llf.auth.AuthUser;
import com.llf.dto.assistant.AiAssistantMessageRequestDTO;
import com.llf.vo.assistant.AiAssistantTurnVO;

public interface AiAssistantService {
    AiAssistantTurnVO createSession(AuthUser currentUser);

    AiAssistantTurnVO message(AuthUser currentUser, AiAssistantMessageRequestDTO dto);

    AiAssistantTurnVO confirm(AuthUser currentUser, String executionId);

    AiAssistantTurnVO cancel(AuthUser currentUser, String executionId);
}
