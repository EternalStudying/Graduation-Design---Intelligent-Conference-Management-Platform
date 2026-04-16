package com.llf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NotificationReadAllDTO {
    @NotBlank(message = "category must not be blank")
    private String category;
}
