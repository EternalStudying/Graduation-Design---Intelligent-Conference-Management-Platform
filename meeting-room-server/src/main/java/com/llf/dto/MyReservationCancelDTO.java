package com.llf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MyReservationCancelDTO {
    @NotBlank(message = "cancelReason must not be blank")
    private String cancelReason;
}
