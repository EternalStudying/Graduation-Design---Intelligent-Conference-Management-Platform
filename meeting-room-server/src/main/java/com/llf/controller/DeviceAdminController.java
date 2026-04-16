package com.llf.controller;

import com.llf.dto.admin.device.AdminDeviceStatusDTO;
import com.llf.dto.admin.device.AdminDeviceUpsertDTO;
import com.llf.result.R;
import com.llf.service.DeviceAdminService;
import com.llf.vo.admin.device.AdminDevicePageVO;
import com.llf.vo.admin.device.AdminDeviceVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/admin/devices")
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
}
