package com.llf.vo;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class DashboardTodayScheduleVO {
    private Long id;
    private Timestamp startTime;
    private Timestamp endTime;
    private String title;
    private Long roomId;
    private String roomName;
    private Integer attendees;
    private String status;
    private String deviceSummary;
}
