package com.llf.dto;

import lombok.Data;

@Data
public class DeviceUpsertDTO {
    /** 设备编码（如 DEV-PROJ） */
    private String code;

    /** 设备名称（如 投影仪） */
    private String name;

    /** 库存 */
    private Integer total;

    /** ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String description;
}