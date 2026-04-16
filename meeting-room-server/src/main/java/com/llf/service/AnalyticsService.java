package com.llf.service;

import com.llf.vo.admin.stats.AdminStatsVO;

public interface AnalyticsService {
    AdminStatsVO adminStats(Integer days);
}
