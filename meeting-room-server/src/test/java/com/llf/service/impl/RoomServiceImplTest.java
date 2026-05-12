package com.llf.service.impl;

import com.llf.dto.admin.room.AdminRoomStatusDTO;
import com.llf.mapper.RoomMapper;
import com.llf.result.BizException;
import com.llf.vo.room.RoomPageItemVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

    @Mock
    private RoomMapper roomMapper;

    @InjectMocks
    private RoomServiceImpl roomService;

    @Test
    void adminPage_shouldAcceptNumericAvailableStatus() {
        when(roomMapper.countRoomsForPage(null, "AVAILABLE", null, null, null, 0)).thenReturn(0L);
        when(roomMapper.countAll()).thenReturn(0);
        when(roomMapper.countAvailable()).thenReturn(0);
        when(roomMapper.countMaintenance()).thenReturn(0);
        when(roomMapper.countUnbound()).thenReturn(0);
        when(roomMapper.countLarge()).thenReturn(0);

        roomService.adminPage(1, 10, null, "1", null, null);

        verify(roomMapper).countRoomsForPage(null, "AVAILABLE", null, null, null, 0);
    }

    @Test
    void pageRooms_shouldPassParsedDeviceIdsToMapper() {
        when(roomMapper.countRoomsForPage(null, null, null, null, List.of(1L, 2L), 2)).thenReturn(0L);
        when(roomMapper.countAll()).thenReturn(0);
        when(roomMapper.countAvailable()).thenReturn(0);
        when(roomMapper.countMaintenance()).thenReturn(0);
        when(roomMapper.countUnbound()).thenReturn(0);
        when(roomMapper.countLarge()).thenReturn(0);

        roomService.pageRooms(1, 10, null, null, null, null, "1, 2");

        verify(roomMapper).countRoomsForPage(null, null, null, null, List.of(1L, 2L), 2);
    }

    @Test
    void pageRooms_shouldRejectInvalidDeviceIds() {
        BizException ex = assertThrows(
                BizException.class,
                () -> roomService.pageRooms(1, 10, null, null, null, null, "1,x")
        );

        assertEquals(400, ex.getCode());
    }

    @Test
    void adminUpdateStatus_shouldAcceptNumericMaintenanceStatus() {
        when(roomMapper.selectRoomById(10L)).thenReturn(room(10L));
        AdminRoomStatusDTO dto = new AdminRoomStatusDTO();
        dto.setStatus("2");
        dto.setMaintenanceRemark("检修");

        roomService.adminUpdateStatus(10L, dto);

        verify(roomMapper).updateStatusById(eq(10L), eq("MAINTENANCE"), eq("检修"));
    }

    private RoomPageItemVO room(Long id) {
        RoomPageItemVO room = new RoomPageItemVO();
        room.setId(id);
        room.setStatus("AVAILABLE");
        return room;
    }
}
