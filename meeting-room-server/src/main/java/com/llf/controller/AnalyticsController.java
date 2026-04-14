package com.llf.controller;

import com.llf.dto.AnalyticsQueryDTO;
import com.llf.result.R;
import com.llf.service.AnalyticsService;
import com.llf.vo.analytics.AnalyticsOverviewVO;
import jakarta.annotation.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Resource
    private AnalyticsService analyticsService;

    @GetMapping("/overview")
    public R<AnalyticsOverviewVO> overview(AnalyticsQueryDTO q) {
        return R.ok(analyticsService.overview(q));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(AnalyticsQueryDTO q) {
        String csv = analyticsService.exportCsv(q);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=analytics.csv")
                .contentType(new MediaType("text", "csv"))
                .body(bytes);
    }
}