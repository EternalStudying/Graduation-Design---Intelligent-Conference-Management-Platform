package com.llf.vo;

import lombok.Data;
import java.util.List;

@Data
public class RoomRecommendItemVO {
    private RoomListItemVO room;
    private Integer score;
    private List<String> missingDevices;
    private Integer wastePct;
}