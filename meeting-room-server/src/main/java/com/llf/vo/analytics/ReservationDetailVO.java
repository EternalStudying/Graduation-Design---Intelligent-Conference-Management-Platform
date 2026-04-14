package com.llf.vo.analytics;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class ReservationDetailVO {
    private Long id;
    private String title;
    private String roomCode;
    private String roomName;
    private Timestamp startTime;
    private Timestamp endTime;
    private String status;
}