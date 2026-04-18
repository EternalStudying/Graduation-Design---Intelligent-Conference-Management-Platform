package com.llf.ai;

import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Component
public class SpringAiReservationAssistantLlmClient implements ReservationAssistantLlmClient {

    private static final String SYSTEM_PROMPT = """
            你是会议室预约助手。
            你只能回答会议室和预约相关问题。
            你必须优先基于系统实时事实和知识库片段回答，不能编造不存在的预约或会议室。
            如果信息不足，请明确说明不确定。
            你只输出最终回答，不要展示思考过程，不要输出推理内容，不要自言自语。
            不要复述“系统事实”“知识库片段”“用户问题”等提示标签。
            回答尽量简洁、可执行。
            """;

    private final ChatModel chatModel;
    private final String model;
    private final boolean enabled;
    private final long timeoutMs;
    private final ExecutorService llmExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public SpringAiReservationAssistantLlmClient(
            ChatModel chatModel,
            @Value("${spring.ai.ollama.chat.options.model:qwen2.5:7b}") String model,
            @Value("${assistant.ai.enabled:true}") boolean enabled,
            @Value("${assistant.ai.timeout-ms:10000}") long timeoutMs) {
        this.chatModel = chatModel;
        this.model = model == null || model.isBlank() ? "qwen2.5:7b" : model.trim();
        this.enabled = enabled;
        this.timeoutMs = timeoutMs <= 0 ? 10000 : timeoutMs;
    }

    @Override
    public String generate(ReservationAssistantLlmRequest request) {
        if (!enabled || request == null || request.question() == null || request.question().isBlank()) {
            return null;
        }

        Prompt prompt = new Prompt(
                List.of(
                        new SystemMessage(SYSTEM_PROMPT),
                        new SystemMessage(buildContextPrompt(request)),
                        new UserMessage(request.question().trim())
                ),
                OllamaChatOptions.builder()
                        .model(model)
                        .disableThinking()
                        .build()
        );

        Future<ChatResponse> future = llmExecutor.submit(() -> chatModel.call(prompt));
        try {
            ChatResponse response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return null;
            }

            String content = response.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                return null;
            }
            return content.trim();
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
        llmExecutor.shutdownNow();
    }

    private String buildContextPrompt(ReservationAssistantLlmRequest request) {
        List<String> sections = new ArrayList<>();
        if (request.facts() != null && !request.facts().isBlank()) {
            sections.add("系统事实：\n" + request.facts().trim());
        }
        if (request.knowledgeSnippets() != null && !request.knowledgeSnippets().isEmpty()) {
            StringBuilder knowledge = new StringBuilder("知识库片段：");
            for (String snippet : request.knowledgeSnippets()) {
                if (snippet == null || snippet.isBlank()) {
                    continue;
                }
                knowledge.append("\n- ").append(snippet.trim());
            }
            sections.add(knowledge.toString());
        }
        if (sections.isEmpty()) {
            return "没有额外上下文，请仅基于用户问题回答。";
        }
        sections.add("请结合以上信息直接回答用户问题，不要原样复述这些上下文。");
        return String.join("\n\n", sections);
    }
}
