package com.llf.vo.dashboard;

import lombok.Data;

@Data
public class DashboardOverviewSummaryVO {
    private Integer todayMeetingCount;
    private Integer utilizationRate;
    private Integer pendingCount;
    private Integer availableRoomCount;
    private Integer totalRoomCount;
}

