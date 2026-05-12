package com.llf.vo.admin.reservation;

import lombok.Data;

@Data
public class AdminReservationStatsVO {
    private Integer totalCount;
    private Integer pendingCount;
    private Integer activeCount;
    private Integer rejectedCount;
    private Integer exceptionCount;
}
