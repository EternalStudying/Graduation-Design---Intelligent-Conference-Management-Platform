package com.llf.vo.reservation;

import lombok.Data;

import java.util.List;

@Data
public class ReservationRecommendationItemVO {
    private Long roomId;
    private String roomCode;
    private String roomName;
    private String location;
    private Integer capacity;
    private Integer score;
    private Double wasteRate;
    private Integer requiredDeviceTypeCount;
    private Integer matchedDeviceTypeCount;
    private Boolean deviceFullyMatched;
    private Boolean isPreferred;
    private List<String> tags;
}

