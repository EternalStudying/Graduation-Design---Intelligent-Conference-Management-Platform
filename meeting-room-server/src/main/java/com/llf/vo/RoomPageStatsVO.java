package com.llf.vo;

import lombok.Data;

@Data
public class RoomPageStatsVO {
    private Integer totalCount;
    private Integer availableCount;
    private Integer maintenanceCount;
    private Integer unboundCount;
    private Integer largeRoomCount;
}
