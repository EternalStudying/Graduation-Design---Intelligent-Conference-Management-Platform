package com.llf.controller;

import com.llf.result.R;
import com.llf.service.DeviceAdminService;
import com.llf.vo.DeviceBindingStatsVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/device-stats")
public class AdminDeviceStatsController {

    @Resource
    private DeviceAdminService deviceAdminService;

    @GetMapping
    public R<DeviceBindingStatsVO> stats() {
        return R.ok(deviceAdminService.deviceBindingStats());
    }
}
