package com.llf.vo;

import lombok.Data;

@Data
public class ReservationCreateVO {
    private Long id;
    private String reservationNo;
    private Long roomId;
    private Long organizerId;
    private String title;
    private Integer attendees;
    private String startTime;
    private String endTime;
    private String status;
    private String remark;
}
