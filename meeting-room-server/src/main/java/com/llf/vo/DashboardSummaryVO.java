package com.llf.vo;

import lombok.Data;

@Data
public class DashboardSummaryVO {

    private Integer roomTotal;
    private Integer availableRoomCount;
    private Integer activeReservationCount;
}