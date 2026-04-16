package com.llf.dto.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ReservationRecommendationDTO {

    private String title;

    @NotNull(message = "attendees must not be null")
    @Min(value = 1, message = "attendees must be greater than 0")
    private Integer attendees;

    @NotBlank(message = "startTime must not be blank")
    private String startTime;

    @NotBlank(message = "endTime must not be blank")
    private String endTime;

    private Long preferredRoomId;

    @Valid
    private List<ReservationDeviceRequirementDTO> deviceRequirements;
}

