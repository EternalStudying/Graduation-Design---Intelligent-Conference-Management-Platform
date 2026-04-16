package com.llf.vo.admin.device;

import lombok.Data;

import java.util.List;

@Data
public class DeviceBindingStatsVO {
    private Integer totalBindingCount;
    private Integer boundDeviceTypeCount;
    private Integer boundRoomCount;
    private Integer unboundRoomCount;
    private List<DeviceStatVO> devices;
    private List<RoomStatVO> rooms;

    @Data
    public static class DeviceStatVO {
        private Long id;
        private String deviceCode;
        private String name;
        private Integer total;
        private String status;
        private Integer boundRoomCount;
        private List<DeviceRoomVO> rooms;
        private Double bindingRate;
    }

    @Data
    public static class DeviceRoomVO {
        private Long roomId;
        private String roomCode;
        private String roomName;
        private String location;
    }

    @Data
    public static class RoomStatVO {
        private Long roomId;
        private String roomCode;
        private String roomName;
        private String location;
        private String roomStatus;
        private Integer deviceTypeCount;
        private List<RoomBoundDeviceVO> boundDevices;
        private String bindingLevel;
    }

    @Data
    public static class RoomBoundDeviceVO {
        private Long deviceId;
        private String deviceCode;
        private String name;
        private String status;
    }
}

