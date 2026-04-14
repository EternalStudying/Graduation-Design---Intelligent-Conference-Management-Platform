package com.llf.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminDeviceUpsertDTO {
    @NotBlank(message = "deviceCode must not be blank")
    private String deviceCode;

    @NotBlank(message = "name must not be blank")
    private String name;

    @NotNull(message = "total must not be null")
    @Min(value = 1, message = "total must be greater than 0")
    private Integer total;

    @NotBlank(message = "status must not be blank")
    private String status;
}
