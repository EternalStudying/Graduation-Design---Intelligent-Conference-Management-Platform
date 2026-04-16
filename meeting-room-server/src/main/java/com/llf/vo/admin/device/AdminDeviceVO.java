package com.llf.vo.admin.device;

import lombok.Data;

import java.util.List;

@Data
public class AdminDeviceVO {
    private Long id;
    private String deviceCode;
    private String name;
    private Integer total;
    private String status;
    private Integer boundRoomCount;
    private Integer boundQuantity;
    private Integer availableQuantity;
    private List<BoundRoomVO> boundRooms;

    @Data
    public static class BoundRoomVO {
        private Long roomId;
        private String roomCode;
        private String roomName;
        private String location;
        private Integer quantity;
    }
}

