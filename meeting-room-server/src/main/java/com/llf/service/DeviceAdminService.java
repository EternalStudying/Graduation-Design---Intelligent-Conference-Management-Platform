package com.llf.service;

import com.llf.dto.admin.device.AdminDeviceStatusDTO;
import com.llf.dto.admin.device.AdminDeviceUpsertDTO;
import com.llf.vo.admin.device.AdminDevicePageVO;
import com.llf.vo.admin.device.AdminDeviceVO;
import com.llf.vo.admin.device.DeviceBindingStatsVO;

public interface DeviceAdminService {
    AdminDevicePageVO adminPage(Integer currentPage, Integer size, String keyword, String status);

    AdminDeviceVO adminDetail(Long id);

    Long adminCreate(AdminDeviceUpsertDTO dto);

    void adminUpdate(Long id, AdminDeviceUpsertDTO dto);

    void adminUpdateStatus(Long id, AdminDeviceStatusDTO dto);

    void adminDelete(Long id);

    DeviceBindingStatsVO deviceBindingStats();
}
