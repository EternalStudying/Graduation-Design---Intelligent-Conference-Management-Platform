package com.llf.dto.admin.room;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AdminRoomDevicesDTO {
    @Valid
    private List<DeviceBindingDTO> devices;

    @Data
    public static class DeviceBindingDTO {
        @NotNull(message = "deviceId must not be null")
        private Long deviceId;

        @NotNull(message = "quantity must not be null")
        @Min(value = 1, message = "quantity must be greater than 0")
        private Integer quantity;
    }
}

