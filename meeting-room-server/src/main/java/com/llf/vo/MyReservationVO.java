package com.llf.vo;

import lombok.Data;

import java.util.List;

@Data
public class MyReservationVO {
    private Long id;
    private String reservationNo;
    private Long roomId;
    private String roomCode;
    private String roomName;
    private String roomLocation;
    private Integer roomCapacity;
    private String roomDescription;
    private Long organizerId;
    private String organizerName;
    private String title;
    private String startTime;
    private String endTime;
    private Integer attendees;
    private String status;
    private String remark;
    private String cancelReason;
    private String role;
    private Boolean canEdit;
    private Boolean canCancel;
    private List<DeviceVO> devices;

    @Data
    public static class DeviceVO {
        private Long id;
        private Long deviceId;
        private String deviceCode;
        private String name;
        private Integer quantity;
        private String status;
    }

    @Data
    public static class DeviceRow {
        private Long reservationId;
        private Long id;
        private Long deviceId;
        private String deviceCode;
        private String name;
        private Integer quantity;
        private String status;
    }
}
