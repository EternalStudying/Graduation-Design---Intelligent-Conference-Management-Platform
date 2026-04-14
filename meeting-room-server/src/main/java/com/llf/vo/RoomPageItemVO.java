package com.llf.vo;

import lombok.Data;

import java.util.List;

@Data
public class RoomPageItemVO {
    private Long id;
    private String roomCode;
    private String name;
    private String location;
    private Integer capacity;
    private String status;
    private String description;
    private String maintenanceRemark;
    private List<RoomPageDeviceVO> devices;
    private Integer deviceCount;
    private String deviceBindingSummary;
}
