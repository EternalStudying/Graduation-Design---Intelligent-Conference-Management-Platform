package com.llf.service;

import com.llf.vo.DashboardSummaryVO;
import com.llf.vo.DashboardOverviewVO;
import com.llf.vo.DashboardQuoteVO;

public interface DashboardService {

    DashboardSummaryVO getSummary();

    DashboardOverviewVO getOverview();

    DashboardQuoteVO getQuote();
}
