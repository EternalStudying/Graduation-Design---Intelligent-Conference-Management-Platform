package com.llf.vo;

import lombok.Data;

@Data
public class ReservationBriefVO {
    private Long id;
    private String title;
    private String roomId;
    private String start;
    private String end;
}