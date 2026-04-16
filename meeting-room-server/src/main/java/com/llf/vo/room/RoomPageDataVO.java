package com.llf.vo.room;

import lombok.Data;

import java.util.List;

@Data
public class RoomPageDataVO {
    private List<RoomPageItemVO> list;
    private Long total;
    private RoomPageStatsVO stats;
}

