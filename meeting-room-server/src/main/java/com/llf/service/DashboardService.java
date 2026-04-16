package com.llf.service;

import com.llf.vo.dashboard.DashboardOverviewVO;
import com.llf.vo.dashboard.DashboardQuoteVO;

public interface DashboardService {

    DashboardOverviewVO getOverview();

    DashboardQuoteVO getQuote();
}
