package com.llf.dto.admin.room;

import lombok.Data;

import java.util.List;

@Data
public class RoomUpsertDTO {
    private String roomCode;
    private String name;
    private String location;
    private Integer capacity;
    private String status;
    private String description;
    private String maintenanceRemark;
    private List<Long> deviceIds;
}

