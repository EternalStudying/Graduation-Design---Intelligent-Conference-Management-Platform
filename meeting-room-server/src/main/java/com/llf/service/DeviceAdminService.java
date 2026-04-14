package com.llf.service;

import com.llf.dto.AdminDeviceStatusDTO;
import com.llf.dto.AdminDeviceUpsertDTO;
import com.llf.dto.DeviceUpsertDTO;
import com.llf.vo.AdminDevicePageVO;
import com.llf.vo.AdminDeviceVO;
import com.llf.vo.DeviceAdminVO;
import com.llf.vo.DeviceBindingStatsVO;
import com.llf.vo.DeviceConcurrencyDetailVO;
import com.llf.vo.DeviceConcurrencyVO;

import java.util.List;

public interface DeviceAdminService {
    AdminDevicePageVO adminPage(Integer currentPage, Integer size, String keyword, String status);

    AdminDeviceVO adminDetail(Long id);

    Long adminCreate(AdminDeviceUpsertDTO dto);

    void adminUpdate(Long id, AdminDeviceUpsertDTO dto);

    void adminUpdateStatus(Long id, AdminDeviceStatusDTO dto);

    void adminDelete(Long id);

    DeviceBindingStatsVO deviceBindingStats();

    List<DeviceAdminVO> list(String keyword, String status);

    void create(DeviceUpsertDTO dto);

    void update(String code, DeviceUpsertDTO dto);

    Integer delete(String code, boolean force);

    List<DeviceConcurrencyVO> deviceConcurrencyStat();

    DeviceConcurrencyDetailVO deviceConcurrencyDetail(String deviceCode);
}
