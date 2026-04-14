package com.llf.service;

import com.llf.vo.RoomRecommendItemVO;

import java.util.List;

public interface RoomRecommendService {
    List<RoomRecommendItemVO> recommend(String startIso, String endIso, Integer attendees, String requiredDevicesCsv);
}