package com.llf.service;

import com.llf.dto.AdminRoomDevicesDTO;
import com.llf.dto.AdminRoomStatusDTO;
import com.llf.dto.RoomUpsertDTO;
import com.llf.vo.RoomAdminDetailVO;
import com.llf.vo.RoomListItemVO;
import com.llf.vo.RoomPageDataVO;
import com.llf.vo.RoomPageItemVO;

import java.util.List;

public interface RoomService {
    List<RoomListItemVO> listRooms(String keyword, Long deviceId, Boolean onlyAvailable);

    RoomPageDataVO pageRooms(Integer currentPage,
                             Integer size,
                             String keyword,
                             String status,
                             String capacityType,
                             String location);

    Long adminCreate(RoomUpsertDTO dto);

    void adminUpdate(RoomUpsertDTO dto);

    void adminDelete(String roomCode);

    RoomAdminDetailVO adminDetail(String roomCode);

    RoomPageDataVO adminPage(Integer currentPage,
                             Integer size,
                             String keyword,
                             String status,
                             String capacityType,
                             String location);

    RoomPageItemVO adminDetailById(Long id);

    Long adminCreateV2(RoomUpsertDTO dto);

    void adminUpdateV2(Long id, RoomUpsertDTO dto);

    void adminUpdateStatus(Long id, AdminRoomStatusDTO dto);

    void adminDeleteById(Long id);

    void adminUpdateDevices(Long id, AdminRoomDevicesDTO dto);
}
