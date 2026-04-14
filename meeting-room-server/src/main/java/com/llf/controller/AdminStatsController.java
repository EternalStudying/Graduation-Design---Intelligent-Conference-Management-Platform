package com.llf.controller;

import com.llf.result.R;
import com.llf.service.AnalyticsService;
import com.llf.vo.analytics.AdminStatsVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    @Resource
    private AnalyticsService analyticsService;

    @GetMapping
    public R<AdminStatsVO> stats(@RequestParam(required = false) Integer days) {
        return R.ok(analyticsService.adminStats(days));
    }
}
