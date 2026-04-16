package com.llf.vo.admin.stats;

import lombok.Data;

import java.util.List;

@Data
public class AdminStatsVO {
    private Integer recentDays;
    private KpisVO kpis;
    private List<TrendItemVO> trend;
    private List<HeatmapItemVO> heatmap;
    private List<RoomUsageRankVO> roomUsageRanks;
    private List<DeviceUsageRankVO> deviceUsageRanks;
    private CoverageVO coverage;
    private List<AlertVO> alerts;
    private List<ReservationItemVO> reservations;
    private StaticSummaryVO staticSummary;

    @Data
    public static class KpisVO {
        private KpiCardVO totalReservations;
        private KpiCardVO activeReservations;
        private KpiCardVO cancelledReservations;
        private KpiCardVO roomCoverageRate;
        private KpiCardVO deviceCoverageRate;
        private KpiCardVO maintenanceRooms;
    }

    @Data
    public static class KpiCardVO {
        private String key;
        private String label;
        private Long value;
        private String unit;
        private String detail;
        private String tone;
    }

    @Data
    public static class TrendItemVO {
        private String date;
        private String label;
        private Long reservationCount;
    }

    @Data
    public static class HeatmapItemVO {
        private String weekday;
        private Integer weekdayIndex;
        private String hour;
        private Integer hourIndex;
        private Long reservationCount;
    }

    @Data
    public static class RoomUsageRankVO {
        private Long roomId;
        private String roomCode;
        private String roomName;
        private String location;
        private Long reservationCount;
        private Long activeCount;
        private Long cancelledCount;
    }

    @Data
    public static class DeviceUsageRankVO {
        private Long deviceId;
        private String deviceCode;
        private String deviceName;
        private String status;
        private Long usageQuantity;
        private Long reservationCount;
    }

    @Data
    public static class CoverageVO {
        private Integer configuredRoomCount;
        private Integer unconfiguredRoomCount;
    }

    @Data
    public static class AlertVO {
        private String id;
        private String type;
        private String targetName;
        private String summary;
        private String level;
    }

    @Data
    public static class ReservationItemVO {
        private Long id;
        private String reservationNo;
        private String title;
        private String roomName;
        private String organizerName;
        private String startTime;
        private String endTime;
        private String status;
        private String deviceSummary;
    }

    @Data
    public static class StaticSummaryVO {
        private Integer totalRooms;
        private Integer availableRooms;
        private Integer maintenanceRooms;
        private Integer totalDevices;
        private Integer enabledDevices;
        private Integer disabledDevices;
    }
}

