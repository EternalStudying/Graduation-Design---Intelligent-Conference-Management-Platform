package com.llf.vo;

import lombok.Data;

@Data
public class DashboardRoomStatusVO {
    private Long roomId;
    private String roomName;
    private String status;
    private String displayStatus;
    private String detail;
}
