package com.llf.dto.assistant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequestDTO {
    private String sessionId;

    @NotBlank(message = "message must not be blank")
    private String message;

    private String scene;
}
