package com.llf.dto.admin.reservation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminReservationReasonDTO {
    @NotBlank(message = "reason must not be blank")
    private String reason;
}
