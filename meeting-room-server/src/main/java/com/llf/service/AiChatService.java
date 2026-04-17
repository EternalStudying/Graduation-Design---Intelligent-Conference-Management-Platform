package com.llf.service;

import com.llf.auth.AuthUser;
import com.llf.vo.assistant.AiChatResponseVO;
import com.llf.vo.assistant.AiChatSessionVO;

public interface AiChatService {
    AiChatSessionVO createSession(AuthUser currentUser);

    AiChatResponseVO chat(AuthUser currentUser, String sessionId, String message, String scene);
}
