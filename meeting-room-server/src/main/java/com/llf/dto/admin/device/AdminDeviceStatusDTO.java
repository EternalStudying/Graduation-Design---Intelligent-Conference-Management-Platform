package com.llf.dto.admin.device;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminDeviceStatusDTO {
    @NotBlank(message = "status must not be blank")
    private String status;
}

