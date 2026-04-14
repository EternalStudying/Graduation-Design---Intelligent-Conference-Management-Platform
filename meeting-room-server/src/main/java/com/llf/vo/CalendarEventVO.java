package com.llf.vo;

import lombok.Data;

import java.util.List;

@Data
public class CalendarEventVO {
    private Long id;
    private String reservationNo;
    private Long roomId;
    private String roomName;
    private Long organizerId;
    private String organizerName;
    private String title;
    private Integer attendees;
    private String startTime;
    private String endTime;
    private String status;
    private String cancelReason;
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
