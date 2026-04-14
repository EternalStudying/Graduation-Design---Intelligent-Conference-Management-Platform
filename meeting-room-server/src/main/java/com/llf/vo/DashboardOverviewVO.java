package com.llf.vo;

import lombok.Data;

import java.util.List;

@Data
public class DashboardOverviewVO {
    private String peakWindow;
    private DashboardOverviewSummaryVO summary;
    private List<DashboardTodayScheduleVO> todaySchedules;
    private List<DashboardRoomStatusVO> roomStatuses;
    private List<String> todoItems;
}
