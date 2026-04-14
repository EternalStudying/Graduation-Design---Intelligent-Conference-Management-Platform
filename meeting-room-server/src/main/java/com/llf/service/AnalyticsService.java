package com.llf.service;

import com.llf.dto.AnalyticsQueryDTO;
import com.llf.vo.analytics.AdminStatsVO;
import com.llf.vo.analytics.AnalyticsOverviewVO;

public interface AnalyticsService {
    AnalyticsOverviewVO overview(AnalyticsQueryDTO q);
    AdminStatsVO adminStats(Integer days);
    String exportCsv(AnalyticsQueryDTO q);
}
