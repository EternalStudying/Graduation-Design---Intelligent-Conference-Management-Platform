package com.llf.vo;

import lombok.Data;

@Data
public class DeviceAdminVO {
    private String code;        // device_code
    private String name;
    private Integer total;
    private String status;      // ENABLED / DISABLED
    private String description; // 备注
    private Integer usedCount;  // 被使用次数（room_device 引用次数）
}