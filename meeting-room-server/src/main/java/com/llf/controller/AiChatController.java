package com.llf.controller;

import com.llf.auth.AuthContext;
import com.llf.dto.assistant.AiChatRequestDTO;
import com.llf.result.R;
import com.llf.service.AiChatService;
import com.llf.vo.assistant.AiChatResponseVO;
import com.llf.vo.assistant.AiChatSessionVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat")
public class AiChatController {

    @Resource
    private AiChatService aiChatService;

    @PostMapping("/session")
    public R<AiChatSessionVO> createSession() {
        return R.ok(aiChatService.createSession(AuthContext.get()));
    }

    @PostMapping
    public R<AiChatResponseVO> chat(@Valid @RequestBody AiChatRequestDTO dto) {
        return R.ok(aiChatService.chat(AuthContext.get(), dto.getSessionId(), dto.getMessage(), dto.getScene()));
    }
}
