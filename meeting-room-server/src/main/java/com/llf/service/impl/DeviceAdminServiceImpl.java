package com.llf.service.impl;

import com.llf.dto.admin.device.AdminDeviceStatusDTO;
import com.llf.dto.admin.device.AdminDeviceUpsertDTO;
import com.llf.mapper.DeviceAdminMapper;
import com.llf.result.BizException;
import com.llf.service.DeviceAdminService;
import com.llf.vo.admin.device.AdminDevicePageVO;
import com.llf.vo.admin.device.AdminDeviceStatsVO;
import com.llf.vo.admin.device.AdminDeviceVO;
import com.llf.vo.admin.device.DeviceBindingStatsVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DeviceAdminServiceImpl implements DeviceAdminService {

    private static final Set<String> DEVICE_STATUS = Set.of("ENABLED", "DISABLED");

    @Resource
    private DeviceAdminMapper deviceAdminMapper;

    @Override
    public AdminDevicePageVO adminPage(Integer currentPage, Integer size, String keyword, String status) {
        String normalizedStatus = normalizeStatusNullable(status);
        int offset = (currentPage - 1) * size;
        Long total = deviceAdminMapper.countAdminPage(keyword, normalizedStatus);

        List<AdminDeviceVO> list = total != null && total > 0
                ? deviceAdminMapper.selectAdminPage(keyword, normalizedStatus, offset, size)
                : new ArrayList<>();
        fillBoundRooms(list);

        AdminDeviceStatsVO stats = new AdminDeviceStatsVO();
        stats.setTotalCount(defaultZero(deviceAdminMapper.countAll()));
        stats.setEnabledCount(defaultZero(deviceAdminMapper.countEnabled()));
        stats.setDisabledCount(defaultZero(deviceAdminMapper.countDisabled()));
        stats.setWarningCount(defaultZero(deviceAdminMapper.countWarning()));

        AdminDevicePageVO page = new AdminDevicePageVO();
        page.setList(list);
        page.setTotal(total == null ? 0L : total);
        page.setStats(stats);
        return page;
    }

    @Override
    public AdminDeviceVO adminDetail(Long id) {
        AdminDeviceVO device = requireAdminDevice(id);
        fillBoundRooms(List.of(device));
        return device;
    }

    @Override
    @Transactional
    public Long adminCreate(AdminDeviceUpsertDTO dto) {
        validateAdminUpsert(dto, null);

        DeviceAdminMapper.AdminDeviceCreateRow row = new DeviceAdminMapper.AdminDeviceCreateRow();
        row.deviceCode = dto.getDeviceCode().trim();
        row.name = dto.getName().trim();
        row.total = dto.getTotal();
        row.status = normalizeStatus(dto.getStatus());
        deviceAdminMapper.insertAdminDevice(row);
        return row.id;
    }

    @Override
    @Transactional
    public void adminUpdate(Long id, AdminDeviceUpsertDTO dto) {
        requireAdminDevice(id);
        validateAdminUpsert(dto, id);

        int boundQuantity = defaultZero(deviceAdminMapper.sumBoundQuantity(id));
        if (dto.getTotal() < boundQuantity) {
            throw new BizException(400, "total must be greater than or equal to bound quantity");
        }

        DeviceAdminMapper.AdminDeviceUpdateRow row = new DeviceAdminMapper.AdminDeviceUpdateRow();
        row.id = id;
        row.deviceCode = dto.getDeviceCode().trim();
        row.name = dto.getName().trim();
        row.total = dto.getTotal();
        row.status = normalizeStatus(dto.getStatus());
        deviceAdminMapper.updateById(row);
    }

    @Override
    @Transactional
    public void adminUpdateStatus(Long id, AdminDeviceStatusDTO dto) {
        requireAdminDevice(id);
        deviceAdminMapper.updateStatusById(id, normalizeStatus(dto.getStatus()));
    }

    @Override
    @Transactional
    public void adminDelete(Long id) {
        requireAdminDevice(id);
        if (deviceAdminMapper.countRoomBindings(id) > 0) {
            throw new BizException(400, "device is still bound to rooms");
        }
        deviceAdminMapper.deleteById(id);
    }

    @Override
    public DeviceBindingStatsVO deviceBindingStats() {
        List<DeviceAdminMapper.BindingRoomRow> roomRows = deviceAdminMapper.selectBindingRooms();
        List<DeviceAdminMapper.BindingDeviceRow> deviceRows = deviceAdminMapper.selectBindingDevices();
        List<DeviceAdminMapper.DeviceBindingRelationRow> relationRows = deviceAdminMapper.selectBindingRelations();

        Map<Long, List<DeviceAdminMapper.DeviceBindingRelationRow>> roomRelationMap = relationRows.stream()
                .collect(Collectors.groupingBy(row -> row.roomId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<DeviceAdminMapper.DeviceBindingRelationRow>> deviceRelationMap = relationRows.stream()
                .collect(Collectors.groupingBy(row -> row.deviceId, LinkedHashMap::new, Collectors.toList()));

        List<DeviceBindingStatsVO.DeviceStatVO> devices = new ArrayList<>();
        for (DeviceAdminMapper.BindingDeviceRow deviceRow : deviceRows) {
            List<DeviceAdminMapper.DeviceBindingRelationRow> rows = deviceRelationMap.getOrDefault(deviceRow.id, List.of());

            DeviceBindingStatsVO.DeviceStatVO device = new DeviceBindingStatsVO.DeviceStatVO();
            device.setId(deviceRow.id);
            device.setDeviceCode(deviceRow.deviceCode);
            device.setName(deviceRow.name);
            device.setTotal(deviceRow.total);
            device.setStatus(deviceRow.status);
            device.setBoundRoomCount(rows.size());
            device.setRooms(rows.stream().map(this::toDeviceRoomVO).toList());
            device.setBindingRate(roomRows.isEmpty() ? 0D : rows.size() * 1.0D / roomRows.size());
            devices.add(device);
        }

        List<DeviceBindingStatsVO.RoomStatVO> rooms = new ArrayList<>();
        int totalBindingCount = 0;
        int boundRoomCount = 0;
        for (DeviceAdminMapper.BindingRoomRow roomRow : roomRows) {
            List<DeviceAdminMapper.DeviceBindingRelationRow> rows = roomRelationMap.getOrDefault(roomRow.id, List.of());
            int deviceTypeCount = rows.size();

            DeviceBindingStatsVO.RoomStatVO room = new DeviceBindingStatsVO.RoomStatVO();
            room.setRoomId(roomRow.id);
            room.setRoomCode(roomRow.roomCode);
            room.setRoomName(roomRow.roomName);
            room.setLocation(roomRow.location);
            room.setRoomStatus(roomRow.roomStatus);
            room.setDeviceTypeCount(deviceTypeCount);
            room.setBoundDevices(rows.stream().map(this::toRoomBoundDeviceVO).toList());
            room.setBindingLevel(toBindingLevel(deviceTypeCount));
            rooms.add(room);

            totalBindingCount += deviceTypeCount;
            if (deviceTypeCount > 0) {
                boundRoomCount++;
            }
        }

        DeviceBindingStatsVO stats = new DeviceBindingStatsVO();
        stats.setTotalBindingCount(totalBindingCount);
        stats.setBoundDeviceTypeCount((int) devices.stream().filter(device -> device.getBoundRoomCount() != null && device.getBoundRoomCount() > 0).count());
        stats.setBoundRoomCount(boundRoomCount);
        stats.setUnboundRoomCount(Math.max(roomRows.size() - boundRoomCount, 0));
        stats.setDevices(devices);
        stats.setRooms(rooms);
        return stats;
    }

    private void validateAdminUpsert(AdminDeviceUpsertDTO dto, Long id) {
        if (dto == null) {
            throw new BizException(400, "request body must not be null");
        }
        if (dto.getDeviceCode() == null || dto.getDeviceCode().isBlank()) {
            throw new BizException(400, "deviceCode must not be blank");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BizException(400, "name must not be blank");
        }
        if (dto.getTotal() == null || dto.getTotal() <= 0) {
            throw new BizException(400, "total must be greater than 0");
        }

        normalizeStatus(dto.getStatus());
        int duplicated = id == null
                ? deviceAdminMapper.countByDeviceCode(dto.getDeviceCode().trim())
                : deviceAdminMapper.countByDeviceCodeExcludeId(id, dto.getDeviceCode().trim());
        if (duplicated > 0) {
            throw new BizException(400, "deviceCode already exists");
        }
    }

    private AdminDeviceVO requireAdminDevice(Long id) {
        AdminDeviceVO device = deviceAdminMapper.selectAdminDetailById(id);
        if (device == null) {
            throw new BizException(404, "device not found");
        }
        return device;
    }

    private void fillBoundRooms(List<AdminDeviceVO> devices) {
        if (devices == null || devices.isEmpty()) {
            return;
        }

        List<Long> deviceIds = devices.stream().map(AdminDeviceVO::getId).toList();
        Map<Long, List<AdminDeviceVO.BoundRoomVO>> boundRoomMap = deviceAdminMapper.selectBoundRoomsByDeviceIds(deviceIds)
                .stream()
                .collect(Collectors.groupingBy(
                        row -> row.deviceId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toBoundRoomVO, Collectors.toList())
                ));

        for (AdminDeviceVO device : devices) {
            device.setBoundRoomCount(defaultZero(device.getBoundRoomCount()));
            device.setBoundQuantity(defaultZero(device.getBoundQuantity()));
            device.setAvailableQuantity(defaultZero(device.getAvailableQuantity()));
            device.setBoundRooms(boundRoomMap.getOrDefault(device.getId(), List.of()));
        }
    }

    private AdminDeviceVO.BoundRoomVO toBoundRoomVO(DeviceAdminMapper.DeviceBoundRoomRow row) {
        AdminDeviceVO.BoundRoomVO vo = new AdminDeviceVO.BoundRoomVO();
        vo.setRoomId(row.roomId);
        vo.setRoomCode(row.roomCode);
        vo.setRoomName(row.roomName);
        vo.setLocation(row.location);
        vo.setQuantity(row.quantity);
        return vo;
    }

    private DeviceBindingStatsVO.DeviceRoomVO toDeviceRoomVO(DeviceAdminMapper.DeviceBindingRelationRow row) {
        DeviceBindingStatsVO.DeviceRoomVO vo = new DeviceBindingStatsVO.DeviceRoomVO();
        vo.setRoomId(row.roomId);
        vo.setRoomCode(row.roomCode);
        vo.setRoomName(row.roomName);
        vo.setLocation(row.location);
        return vo;
    }

    private DeviceBindingStatsVO.RoomBoundDeviceVO toRoomBoundDeviceVO(DeviceAdminMapper.DeviceBindingRelationRow row) {
        DeviceBindingStatsVO.RoomBoundDeviceVO vo = new DeviceBindingStatsVO.RoomBoundDeviceVO();
        vo.setDeviceId(row.deviceId);
        vo.setDeviceCode(row.deviceCode);
        vo.setName(row.deviceName);
        vo.setStatus(row.deviceStatus);
        return vo;
    }

    private String toBindingLevel(int deviceTypeCount) {
        if (deviceTypeCount <= 0) {
            return "none";
        }
        if (deviceTypeCount <= 2) {
            return "light";
        }
        if (deviceTypeCount <= 4) {
            return "medium";
        }
        return "heavy";
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BizException(400, "status must not be blank");
        }
        String value = status.trim();
        if (!DEVICE_STATUS.contains(value)) {
            throw new BizException(400, "status must be one of ENABLED, DISABLED");
        }
        return value;
    }

    private String normalizeStatusNullable(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return normalizeStatus(status);
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }
}
