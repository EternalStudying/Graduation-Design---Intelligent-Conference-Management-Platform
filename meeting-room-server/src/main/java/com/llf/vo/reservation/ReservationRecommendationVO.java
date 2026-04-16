package com.llf.vo.reservation;

import lombok.Data;

import java.util.List;

@Data
public class ReservationRecommendationVO {
    private List<ReservationRecommendationItemVO> recommendations;
}

