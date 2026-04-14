package com.llf.controller;

import com.llf.dto.AdminDeviceStatusDTO;
import com.llf.dto.AdminDeviceUpsertDTO;
import com.llf.result.R;
import com.llf.service.DeviceAdminService;
import com.llf.vo.AdminDevicePageVO;
import com.llf.vo.AdminDeviceVO;
import com.llf.vo.DeviceAdminVO;
import com.llf.vo.DeviceConcurrencyDetailVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping({"/api/devices/admin", "/api/v1/admin/devices"})
public class DeviceAdminController {

    @Resource
    private DeviceAdminService deviceAdminService;

    @GetMapping
    public R<AdminDevicePageVO> page(@RequestParam @NotNull(message = "currentPage must not be null") @Min(value = 1, message = "currentPage must be greater than 0") Integer currentPage,
                                     @RequestParam @NotNull(message = "size must not be null") @Min(value = 1, message = "size must be greater than 0") Integer size,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String status) {
        return R.ok(deviceAdminService.adminPage(currentPage, size, keyword, status));
    }

    @GetMapping("/{id}")
    public R<AdminDeviceVO> detail(@PathVariable Long id) {
        return R.ok(deviceAdminService.adminDetail(id));
    }

    @GetMapping("/list")
    public R<List<DeviceAdminVO>> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String status) {
        return R.ok(deviceAdminService.list(keyword, status));
    }

    @PostMapping
    public R<Long> create(@Valid @RequestBody AdminDeviceUpsertDTO dto) {
        return R.ok(deviceAdminService.adminCreate(dto));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody AdminDeviceUpsertDTO dto) {
        deviceAdminService.adminUpdate(id, dto);
        return R.ok(null);
    }

    @PatchMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody AdminDeviceStatusDTO dto) {
        deviceAdminService.adminUpdateStatus(id, dto);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        deviceAdminService.adminDelete(id);
        return R.ok(null);
    }

    @GetMapping("/device-concurrency")
    public R<?> stat() {
        return R.ok(deviceAdminService.deviceConcurrencyStat());
    }

    @GetMapping("/device-concurrency/detail/{code}")
    public R<DeviceConcurrencyDetailVO> detail(@PathVariable String code) {
        return R.ok(deviceAdminService.deviceConcurrencyDetail(code));
    }
}
