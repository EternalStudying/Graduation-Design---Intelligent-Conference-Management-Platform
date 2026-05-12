package com.llf.service.impl;

import com.llf.dto.admin.device.AdminDeviceStatusDTO;
import com.llf.mapper.DeviceAdminMapper;
import com.llf.vo.admin.device.AdminDeviceVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAdminServiceImplTest {

    @Mock
    private DeviceAdminMapper deviceAdminMapper;

    @InjectMocks
    private DeviceAdminServiceImpl deviceAdminService;

    @Test
    void adminPage_shouldAcceptNumericEnabledStatus() {
        when(deviceAdminMapper.countAdminPage(null, "ENABLED")).thenReturn(0L);
        when(deviceAdminMapper.countAll()).thenReturn(0);
        when(deviceAdminMapper.countEnabled()).thenReturn(0);
        when(deviceAdminMapper.countDisabled()).thenReturn(0);
        when(deviceAdminMapper.countWarning()).thenReturn(0);

        deviceAdminService.adminPage(1, 10, null, "1");

        verify(deviceAdminMapper).countAdminPage(null, "ENABLED");
    }

    @Test
    void adminUpdateStatus_shouldAcceptNumericDisabledStatus() {
        when(deviceAdminMapper.selectAdminDetailById(8L)).thenReturn(device(8L));
        AdminDeviceStatusDTO dto = new AdminDeviceStatusDTO();
        dto.setStatus("0");

        deviceAdminService.adminUpdateStatus(8L, dto);

        verify(deviceAdminMapper).updateStatusById(8L, "DISABLED");
    }

    private AdminDeviceVO device(Long id) {
        AdminDeviceVO device = new AdminDeviceVO();
        device.setId(id);
        device.setStatus("ENABLED");
        return device;
    }
}
