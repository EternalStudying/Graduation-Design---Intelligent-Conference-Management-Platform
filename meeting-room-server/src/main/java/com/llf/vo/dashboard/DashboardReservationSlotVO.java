package com.llf.vo.dashboard;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class DashboardReservationSlotVO {
    private Long id;
    private Long organizerId;
    private Long roomId;
    private String roomName;
    private String title;
    private Timestamp startTime;
    private Timestamp endTime;
    private Integer attendees;
    private String status;
}

