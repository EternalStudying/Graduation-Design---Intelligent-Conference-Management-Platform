package com.llf.ai;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiReservationAssistantLlmClientTest {

    @Test
    void generate_shouldBuildCompactContextPromptAndParseResponse() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("local answer")))));

        SpringAiReservationAssistantLlmClient client =
                new SpringAiReservationAssistantLlmClient(chatModel, "qwen2.5:7b", true, 10000);
        ReservationAssistantLlmRequest request = new ReservationAssistantLlmRequest(
                "find a room",
                "today 14:00-16:00, 10 attendees, 2 candidate rooms",
                List.of("The assistant only handles reservation-related questions.", "Use real-time room data first.")
        );

        String result = client.generate(request);

        assertEquals("local answer", result);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());

        Prompt prompt = promptCaptor.getValue();
        assertNotNull(prompt);
        assertEquals(3, prompt.getInstructions().size());
        assertTrue(prompt.getInstructions().get(0).getText().contains("不要展示思考过程"));
        assertTrue(prompt.getInstructions().get(1).getText().contains("系统事实"));
        assertTrue(prompt.getInstructions().get(1).getText().contains("知识库片段"));
        assertTrue(!prompt.getInstructions().get(1).getText().contains("最近上下文"));
        assertTrue(!prompt.getInstructions().get(1).getText().contains("建议问题"));
        assertEquals("find a room", prompt.getInstructions().get(2).getText());

        OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
        assertNotNull(options);
        assertEquals("qwen2.5:7b", options.getModel());
        assertEquals(Boolean.FALSE, options.toMap().get("think"));
        assertTrue(!options.toMap().containsKey("num_predict"));
    }

    @Test
    void generate_shouldReturnNullWhenModelResponseTimesOut() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(200);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("too late"))));
        });

        SpringAiReservationAssistantLlmClient client =
                new SpringAiReservationAssistantLlmClient(chatModel, "qwen2.5:7b", true, 50);
        ReservationAssistantLlmRequest request = new ReservationAssistantLlmRequest(
                "check conflict",
                "existing reservation at 15:00",
                List.of("Use real-time reservation data first.")
        );

        assertTimeoutPreemptively(Duration.ofMillis(500), () -> assertNull(client.generate(request)));
    }
}
