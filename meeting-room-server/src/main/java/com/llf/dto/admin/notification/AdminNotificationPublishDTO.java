package com.llf.dto.admin.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminNotificationPublishDTO {
    @NotBlank(message = "type must not be blank")
    private String type;

    @NotBlank(message = "recipientScope must not be blank")
    private String recipientScope;

    @NotBlank(message = "title must not be blank")
    @Size(max = 128, message = "title length must not exceed 128")
    private String title;

    @NotBlank(message = "content must not be blank")
    @Size(max = 500, message = "content length must not exceed 500")
    private String content;
}
