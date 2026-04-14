package com.llf.vo.analytics;

import lombok.Data;

@Data
public class RoomRankingVO {
    private String roomCode;
    private String roomName;
    private long count;
    private double hours;
}