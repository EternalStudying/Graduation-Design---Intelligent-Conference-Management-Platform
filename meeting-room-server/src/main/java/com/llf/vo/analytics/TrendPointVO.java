package com.llf.vo.analytics;

import lombok.Data;

@Data
public class TrendPointVO {
    private String bucket; // 例如 2026-03-01 / 2026-W09 / 2026-03
    private long count;
    private double hours;  // 占用小时数
}