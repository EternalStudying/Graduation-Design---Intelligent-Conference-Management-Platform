package com.llf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminRoomStatusDTO {
    @NotBlank(message = "status must not be blank")
    private String status;
    private String maintenanceRemark;
}
