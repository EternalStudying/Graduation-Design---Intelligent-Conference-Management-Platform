package com.llf.vo;

import lombok.Data;

@Data
public class DeviceConcurrencyVO {

    private String code;
    private String name;
    private String status;
    private Integer total;         // 库存
    private Integer maxConcurrent; // 最大并发
    private Integer shortage;      // 缺口（可能为0）
    private Integer usedRoomsCount;
    private Integer coveragePct;
}