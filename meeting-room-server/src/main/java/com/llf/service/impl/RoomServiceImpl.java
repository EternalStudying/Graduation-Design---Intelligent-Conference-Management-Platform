package com.llf.service.impl;

import com.llf.dto.admin.room.AdminRoomDevicesDTO;
import com.llf.dto.admin.room.AdminRoomStatusDTO;
import com.llf.dto.admin.room.RoomUpsertDTO;
import com.llf.mapper.RoomMapper;
import com.llf.result.BizException;
import com.llf.service.RoomService;
import com.llf.vo.room.RoomPageDataVO;
import com.llf.vo.room.RoomPageDeviceVO;
import com.llf.vo.room.RoomPageItemVO;
import com.llf.vo.room.RoomPageStatsVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoomServiceImpl implements RoomService {

    private static final Set<String> ROOM_STATUS = Set.of("AVAILABLE", "MAINTENANCE");

    @Resource
    private RoomMapper roomMapper;

    @Override
    public RoomPageDataVO pageRooms(Integer currentPage,
                                    Integer size,
                                    String keyword,
                                    String status,
                                    String capacityType,
                                    String location,
                                    String deviceIds) {
        return buildRoomPage(currentPage, size, keyword, status, capacityType, location, parseDeviceIds(deviceIds));
    }

    @Override
    public RoomPageItemVO userDetailById(Long id) {
        RoomPageItemVO room = roomMapper.selectRoomById(id);
        if (room == null) {
            throw new BizException(404, "room not found");
        }
        fillRoomDevices(room);
        return room;
    }

    @Override
    public RoomPageDataVO adminPage(Integer currentPage,
                                    Integer size,
                                    String keyword,
                                    String status,
                                    String capacityType,
                                    String location) {
        String normalizedStatus = normalizeRoomStatusNullable(status);
        validatePageFilters(normalizedStatus, capacityType);
        return buildRoomPage(currentPage, size, keyword, normalizedStatus, capacityType, location, null);
    }

    @Override
    public RoomPageItemVO adminDetailById(Long id) {
        RoomPageItemVO room = roomMapper.selectRoomById(id);
        if (room == null) {
            throw new BizException(404, "room not found");
        }
        fillRoomDevices(room);
        return room;
    }

    @Override
    @Transactional
    public Long adminCreateV2(RoomUpsertDTO dto) {
        validateRoomUpsert(dto, null);
        roomMapper.insertRoom(
                dto.getRoomCode().trim(),
                dto.getName().trim(),
                dto.getLocation().trim(),
                dto.getCapacity(),
                normalizeRoomStatus(dto.getStatus()),
                trimToNull(dto.getDescription()),
                normalizeMaintenanceRemark(dto.getStatus(), dto.getMaintenanceRemark())
        );
        return roomMapper.selectIdByRoomCode(dto.getRoomCode().trim());
    }

    @Override
    @Transactional
    public void adminUpdateV2(Long id, RoomUpsertDTO dto) {
        requireRoom(id);
        validateRoomUpsert(dto, id);
        roomMapper.updateRoomById(
                id,
                dto.getRoomCode().trim(),
                dto.getName().trim(),
                dto.getLocation().trim(),
                dto.getCapacity(),
                normalizeRoomStatus(dto.getStatus()),
                trimToNull(dto.getDescription()),
                normalizeMaintenanceRemark(dto.getStatus(), dto.getMaintenanceRemark())
        );
    }

    @Override
    @Transactional
    public void adminUpdateStatus(Long id, AdminRoomStatusDTO dto) {
        requireRoom(id);
        String status = normalizeRoomStatus(dto.getStatus());
        roomMapper.updateStatusById(id, status, normalizeMaintenanceRemark(status, dto.getMaintenanceRemark()));
    }

    @Override
    @Transactional
    public void adminDeleteById(Long id) {
        requireRoom(id);
        if (roomMapper.countReservationsByRoomId(id) > 0) {
            throw new BizException(400, "room has related reservations and cannot be deleted");
        }
        roomMapper.deleteRoomDevices(id);
        roomMapper.deleteRoomById(id);
    }

    @Override
    @Transactional
    public void adminUpdateDevices(Long id, AdminRoomDevicesDTO dto) {
        requireRoom(id);
        List<AdminRoomDevicesDTO.DeviceBindingDTO> devices = dto.getDevices() == null ? List.of() : dto.getDevices();
        validateBindingDevices(devices);

        roomMapper.deleteRoomDevices(id);
        for (AdminRoomDevicesDTO.DeviceBindingDTO device : devices) {
            roomMapper.insertRoomDeviceWithQuantity(id, device.getDeviceId(), device.getQuantity());
        }
    }

    private RoomPageDataVO buildRoomPage(Integer currentPage,
                                         Integer size,
                                         String keyword,
                                         String status,
                                         String capacityType,
                                         String location,
                                         List<Long> deviceIds) {
        String normalizedStatus = normalizeRoomStatusNullable(status);
        int offset = (currentPage - 1) * size;
        int deviceCount = deviceIds == null ? 0 : deviceIds.size();
        Long total = roomMapper.countRoomsForPage(keyword, normalizedStatus, capacityType, location, deviceIds, deviceCount);
        List<RoomPageItemVO> list = total != null && total > 0
                ? roomMapper.selectRoomPage(keyword, normalizedStatus, capacityType, location, deviceIds, deviceCount, offset, size)
                : new ArrayList<>();

        for (RoomPageItemVO room : list) {
            fillRoomDevices(room);
        }

        RoomPageStatsVO stats = new RoomPageStatsVO();
        stats.setTotalCount(defaultZero(roomMapper.countAll()));
        stats.setAvailableCount(defaultZero(roomMapper.countAvailable()));
        stats.setMaintenanceCount(defaultZero(roomMapper.countMaintenance()));
        stats.setUnboundCount(defaultZero(roomMapper.countUnbound()));
        stats.setLargeRoomCount(defaultZero(roomMapper.countLarge()));

        RoomPageDataVO data = new RoomPageDataVO();
        data.setList(list);
        data.setTotal(total == null ? 0L : total);
        data.setStats(stats);
        return data;
    }

    private void fillRoomDevices(RoomPageItemVO room) {
        List<RoomPageDeviceVO> devices = roomMapper.selectDevicesByRoomId(room.getId());
        room.setDevices(devices);

        int totalQuantity = 0;
        int enabledKinds = 0;
        for (RoomPageDeviceVO device : devices) {
            totalQuantity += device.getQuantity() == null ? 0 : device.getQuantity();
            if ("ENABLED".equalsIgnoreCase(device.getStatus())) {
                enabledKinds++;
            }
        }
        room.setDeviceCount(totalQuantity);
        room.setDeviceBindingSummary(devices.size() + " 绫昏澶?/ " + enabledKinds + " 绫诲彲鐢?");
    }

    private void validateRoomUpsert(RoomUpsertDTO dto, Long roomId) {
        if (dto == null) {
            throw new BizException(400, "request body must not be null");
        }
        if (dto.getRoomCode() == null || dto.getRoomCode().isBlank()) {
            throw new BizException(400, "roomCode must not be blank");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BizException(400, "name must not be blank");
        }
        if (dto.getLocation() == null || dto.getLocation().isBlank()) {
            throw new BizException(400, "location must not be blank");
        }
        if (dto.getCapacity() == null || dto.getCapacity() <= 0) {
            throw new BizException(400, "capacity must be greater than 0");
        }

        normalizeRoomStatus(dto.getStatus());
        int duplicated = roomId == null
                ? roomMapper.countByRoomCode(dto.getRoomCode().trim())
                : roomMapper.countByRoomCodeExcludeId(roomId, dto.getRoomCode().trim());
        if (duplicated > 0) {
            throw new BizException(400, "roomCode already exists");
        }
    }

    private void validatePageFilters(String status, String capacityType) {
        if (status != null && !status.isBlank() && !ROOM_STATUS.contains(normalizeRoomStatus(status))) {
            throw new BizException(400, "status must be one of AVAILABLE, MAINTENANCE");
        }
        if (capacityType != null
                && !capacityType.isBlank()
                && !"small".equals(capacityType)
                && !"medium".equals(capacityType)
                && !"large".equals(capacityType)) {
            throw new BizException(400, "capacityType must be one of small, medium, large");
        }
    }

    private void validateBindingDevices(List<AdminRoomDevicesDTO.DeviceBindingDTO> devices) {
        Set<Long> deviceIds = new HashSet<>();
        for (AdminRoomDevicesDTO.DeviceBindingDTO device : devices) {
            if (device.getDeviceId() == null) {
                throw new BizException(400, "deviceId must not be null");
            }
            if (device.getQuantity() == null || device.getQuantity() <= 0) {
                throw new BizException(400, "quantity must be greater than 0");
            }
            if (!deviceIds.add(device.getDeviceId())) {
                throw new BizException(400, "deviceId must not be duplicated");
            }
        }
        if (!deviceIds.isEmpty()) {
            int existing = roomMapper.countExistingDevices(new ArrayList<>(deviceIds));
            if (existing != deviceIds.size()) {
                throw new BizException(400, "deviceId contains non-existent device");
            }
        }
    }

    private RoomPageItemVO requireRoom(Long id) {
        RoomPageItemVO room = roomMapper.selectRoomById(id);
        if (room == null) {
            throw new BizException(404, "room not found");
        }
        return room;
    }

    private String normalizeRoomStatus(String status) {
        String value = status == null || status.isBlank() ? "AVAILABLE" : status.trim();
        if ("1".equals(value)) {
            return "AVAILABLE";
        }
        if ("2".equals(value)) {
            return "MAINTENANCE";
        }
        if (!ROOM_STATUS.contains(value)) {
            throw new BizException(400, "status must be one of AVAILABLE, MAINTENANCE");
        }
        return value;
    }

    private String normalizeRoomStatusNullable(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return normalizeRoomStatus(status);
    }

    private String normalizeMaintenanceRemark(String status, String maintenanceRemark) {
        if (!"MAINTENANCE".equals(normalizeRoomStatus(status))) {
            return null;
        }
        return trimToNull(maintenanceRemark);
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }

    private List<Long> parseDeviceIds(String deviceIds) {
        if (deviceIds == null || deviceIds.isBlank()) {
            return null;
        }

        Set<Long> values = new LinkedHashSet<>();
        for (String part : deviceIds.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                Long id = Long.valueOf(value);
                if (id <= 0) {
                    throw new NumberFormatException("device id must be positive");
                }
                values.add(id);
            } catch (NumberFormatException e) {
                throw new BizException(400, "deviceIds must be comma-separated positive numbers");
            }
        }
        return values.isEmpty() ? null : new ArrayList<>(values);
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }
}
