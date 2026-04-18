package com.llf.controller;

import com.llf.auth.AuthContext;
import com.llf.dto.assistant.AiAssistantMessageRequestDTO;
import com.llf.result.R;
import com.llf.service.AiAssistantService;
import com.llf.vo.assistant.AiAssistantTurnVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/assistant")
public class AiAssistantController {

    @Resource
    private AiAssistantService aiAssistantService;

    @PostMapping("/session")
    public R<AiAssistantTurnVO> createSession() {
        return R.ok(aiAssistantService.createSession(AuthContext.get()));
    }

    @PostMapping("/message")
    public R<AiAssistantTurnVO> message(@RequestBody(required = false) AiAssistantMessageRequestDTO dto) {
        return R.ok(aiAssistantService.message(AuthContext.get(), dto == null ? new AiAssistantMessageRequestDTO() : dto));
    }

    @PostMapping("/actions/{executionId}/confirm")
    public R<AiAssistantTurnVO> confirm(@PathVariable String executionId) {
        return R.ok(aiAssistantService.confirm(AuthContext.get(), executionId));
    }

    @PostMapping("/actions/{executionId}/cancel")
    public R<AiAssistantTurnVO> cancel(@PathVariable String executionId) {
        return R.ok(aiAssistantService.cancel(AuthContext.get(), executionId));
    }
}
