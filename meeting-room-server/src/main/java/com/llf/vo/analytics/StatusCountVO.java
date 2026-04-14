package com.llf.vo.analytics;

import lombok.Data;

@Data
public class StatusCountVO {
    private String status; // ACTIVE / CANCELLED
    private long count;
}