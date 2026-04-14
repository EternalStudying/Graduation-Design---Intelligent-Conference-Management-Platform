package com.llf.vo.analytics;

import com.llf.vo.PageVO;
import lombok.Data;

import java.util.List;

@Data
public class AnalyticsOverviewVO {
    private AnalyticsKpiVO kpi;
    private List<TrendPointVO> trend;         // 折线
    private List<StatusCountVO> statusDist;   // 饼图
    private List<HeatmapCellVO> heatmap;      // 热力
    private List<RoomRankingVO> rankings;     // 排行
    private PageVO<ReservationDetailVO> details; // 明细分页
}