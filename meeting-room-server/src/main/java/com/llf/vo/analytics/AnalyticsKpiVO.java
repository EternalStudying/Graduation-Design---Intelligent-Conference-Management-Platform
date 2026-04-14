package com.llf.vo.analytics;

import lombok.Data;

@Data
public class AnalyticsKpiVO {
    private long total;          // 总数
    private double utilization;  // 使用率(0-100)
    private long conflicts;      // 冲突对数
    private double cancelRate;   // 取消率(0-100)
}