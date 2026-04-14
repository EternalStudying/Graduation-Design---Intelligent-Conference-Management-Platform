package com.llf.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DeviceConcurrencyDetailVO {
    private String code;
    private String name;
    private String status;   // ENABLED/DISABLED
    private Integer total;

    private Integer maxConcurrent; // 峰值
    private Integer shortage;      // 缺口(>=0)

    private Integer usedRoomsCount;
    private Integer coveragePct;

    private List<ReservationBriefVO> relatedReservations = new ArrayList<>();
}