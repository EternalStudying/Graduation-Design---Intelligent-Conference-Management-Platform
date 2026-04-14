package com.llf.dto;

import lombok.Data;

@Data
public class AnalyticsQueryDTO {
    private Long start;       // 时间戳(ms)，可空
    private Long end;         // 时间戳(ms)，可空
    private String roomCode;  // 会议室编码，可空
    private String dimension; // day/week/month，可空(默认week)
    private String status;    // ACTIVE/CANCELLED，可空
    private Integer page;     // 默认1
    private Integer size;     // 默认10
}