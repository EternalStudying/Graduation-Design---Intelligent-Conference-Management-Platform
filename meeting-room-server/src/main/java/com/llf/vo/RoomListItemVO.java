package com.llf.vo;

import lombok.Data;
import java.util.List;

@Data
public class RoomListItemVO {
    private Long id;              // 数据库主键
    private String roomCode;      // R101 / A101
    private String name;          // 一号会议室
    private String location;      // A座 1F
    private Integer capacity;     // 容量
    private String status;        // AVAILABLE / MAINTENANCE
    private List<String> devices; // 设备名称列表：投影仪/白板...
}