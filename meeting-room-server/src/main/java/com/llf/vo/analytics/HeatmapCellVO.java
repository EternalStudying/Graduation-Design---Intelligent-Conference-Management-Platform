package com.llf.vo.analytics;

import lombok.Data;

@Data
public class HeatmapCellVO {
    private int dow;   // 1-7（按 MySQL DAYOFWEEK：1=周日 ... 7=周六）
    private int hour;  // 0-23
    private long count;
}