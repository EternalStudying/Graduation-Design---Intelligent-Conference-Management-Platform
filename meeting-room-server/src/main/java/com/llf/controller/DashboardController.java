package com.llf.controller;

import com.llf.result.R;
import com.llf.service.DashboardService;
import com.llf.vo.DashboardOverviewVO;
import com.llf.vo.DashboardQuoteVO;
import com.llf.vo.DashboardSummaryVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    @Resource
    private DashboardService dashboardService;

    @GetMapping("/summary")
    public R<DashboardSummaryVO> summary() {
        return R.ok(dashboardService.getSummary());
    }

    @GetMapping("/overview")
    public R<DashboardOverviewVO> overview() {
        return R.ok(dashboardService.getOverview());
    }

    @GetMapping("/quote")
    public R<DashboardQuoteVO> quote() {
        return R.ok(dashboardService.getQuote());
    }
}
