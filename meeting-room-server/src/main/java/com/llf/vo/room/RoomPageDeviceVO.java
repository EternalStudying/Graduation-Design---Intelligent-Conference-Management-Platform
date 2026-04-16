package com.llf.vo.room;

import lombok.Data;

@Data
public class RoomPageDeviceVO {
    private Long id;
    private String deviceCode;
    private String name;
    private Integer quantity;
    private Integer total;
    private String status;
}

