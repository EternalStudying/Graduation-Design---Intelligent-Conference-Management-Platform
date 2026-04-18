package com.llf.assistant.semantic;

import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Component
public class SpringAiAssistantIntentLlmClient implements AiAssistantIntentLlmClient {

    private static final String SYSTEM_PROMPT = """
            你是会议室预约系统的语义解析器。
            你的任务只有一个：把用户中文口语解析成严格 JSON。
            你不能输出解释、不能输出 markdown、不能输出额外文本。
            你只能输出一个 JSON 对象，并且必须符合给定 schema。
            如果不确定，也必须返回 actionType=unknown，并把 needClarification 设为 true。
            """;

    private final ChatModel chatModel;
    private final String model;
    private final long timeoutMs;
    private final boolean enabled;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public SpringAiAssistantIntentLlmClient(ChatModel chatModel,
                                            @Value("${spring.ai.ollama.chat.options.model:qwen2.5:7b}") String model,
                                            @Value("${assistant.ai.enabled:true}") boolean enabled,
                                            @Value("${assistant.ai.timeout-ms:10000}") long timeoutMs) {
        this.chatModel = chatModel;
        this.model = model == null || model.isBlank() ? "qwen2.5:7b" : model.trim();
        this.enabled = enabled;
        this.timeoutMs = timeoutMs <= 0 ? 10000 : timeoutMs;
    }

    @Override
    public String parseIntentJson(AiAssistantIntentLlmRequest request) {
        if (!enabled || request == null || request.originalText() == null || request.originalText().isBlank()) {
            return null;
        }
        Prompt prompt = new Prompt(
                List.of(
                        new SystemMessage(SYSTEM_PROMPT),
                        new UserMessage(buildUserPrompt(request))
                ),
                OllamaChatOptions.builder()
                        .model(model)
                        .disableThinking()
                        .build()
        );

        Future<ChatResponse> future = executorService.submit(() -> chatModel.call(prompt));
        try {
            ChatResponse response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return null;
            }
            String content = response.getResult().getOutput().getText();
            return content == null || content.isBlank() ? null : content.trim();
        } catch (Exception e) {
            future.cancel(true);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdownNow();
    }

    private String buildUserPrompt(AiAssistantIntentLlmRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前 schema：\n").append(request.schemaText()).append("\n\n");
        builder.append("用户原话：").append(request.originalText()).append("\n");
        builder.append("归一化文本：").append(request.normalizedText()).append("\n");
        if (request.currentActionType() != null) {
            builder.append("当前会话动作：").append(request.currentActionType()).append("\n");
        }
        if (request.recentMessages() != null && !request.recentMessages().isEmpty()) {
            builder.append("最近上下文：").append(request.recentMessages()).append("\n");
        }
        builder.append("只输出 JSON，不要输出任何解释。");
        return builder.toString();
    }
}
