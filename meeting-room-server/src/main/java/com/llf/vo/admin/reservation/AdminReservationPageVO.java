package com.llf.vo.admin.reservation;

import lombok.Data;

import java.util.List;

@Data
public class AdminReservationPageVO {
    private List<AdminReservationItemVO> list;
    private Long total;
    private AdminReservationStatsVO stats;
}
