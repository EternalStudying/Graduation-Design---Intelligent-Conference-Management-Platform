package com.llf.controller;

import com.llf.result.R;
import com.llf.mapper.DeviceMapper;
import com.llf.vo.DeviceEnabledVO;
import com.llf.vo.DeviceOptionVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    @Resource
    private DeviceMapper deviceMapper;

    @GetMapping("/options")
    public R<List<DeviceOptionVO>> options() {
        return R.ok(deviceMapper.selectEnabledOptions());
    }

    @GetMapping("/enabled")
    public R<List<DeviceEnabledVO>> enabled() {
        return R.ok(deviceMapper.selectEnabledList());
    }
}