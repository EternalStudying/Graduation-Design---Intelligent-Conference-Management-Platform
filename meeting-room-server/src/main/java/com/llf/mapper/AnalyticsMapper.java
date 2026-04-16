package com.llf.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface AnalyticsMapper {

    @Select("SELECT COUNT(1) FROM meeting_room")
    long countRooms();

    @Select("""
                SELECT COUNT(1)
                FROM reservation
                WHERE start_time >= #{start}
                  AND start_time < #{end}
            """)
    long countRecentReservations(@Param("start") Timestamp start,
                                 @Param("end") Timestamp end);

    @Select("""
                SELECT COUNT(1)
                FROM reservation
                WHERE start_time >= #{start}
                  AND start_time < #{end}
                  AND status = #{status}
            """)
    long countRecentReservationsByStatus(@Param("start") Timestamp start,
                                         @Param("end") Timestamp end,
                                         @Param("status") String status);

    @Select("""
                SELECT COUNT(DISTINCT room_id)
                FROM room_device
            """)
    Integer countConfiguredRooms();

    @Select("SELECT COUNT(1) FROM device")
    Integer countAllDevices();

    @Select("SELECT COUNT(1) FROM device WHERE status = 'ENABLED'")
    Integer countEnabledDevices();

    @Select("SELECT COUNT(1) FROM device WHERE status = 'DISABLED'")
    Integer countDisabledDevices();

    @Select("SELECT COUNT(1) FROM meeting_room WHERE status = 'MAINTENANCE'")
    Integer countMaintenanceRooms();

    @Select("""
                SELECT COUNT(DISTINCT device_id)
                FROM room_device
            """)
    Integer countBoundDeviceTypes();

    @Select("""
                SELECT
                  DATE_FORMAT(start_time, '%Y-%m-%d') AS date,
                  COUNT(1) AS reservationCount
                FROM reservation
                WHERE start_time >= #{start}
                  AND start_time < #{end}
                GROUP BY DATE_FORMAT(start_time, '%Y-%m-%d')
                ORDER BY date ASC
            """)
    List<RecentTrendRow> selectRecentTrend(@Param("start") Timestamp start,
                                           @Param("end") Timestamp end);

    @Select("""
                SELECT
                  CASE
                    WHEN DAYOFWEEK(start_time) = 1 THEN 6
                    ELSE DAYOFWEEK(start_time) - 2
                  END AS weekdayIndex,
                  HOUR(start_time) - 8 AS hourIndex,
                  COUNT(1) AS reservationCount
                FROM reservation
                WHERE start_time >= #{start}
                  AND start_time < #{end}
                  AND HOUR(start_time) BETWEEN 8 AND 21
                GROUP BY
                  CASE
                    WHEN DAYOFWEEK(start_time) = 1 THEN 6
                    ELSE DAYOFWEEK(start_time) - 2
                  END,
                  HOUR(start_time) - 8
                ORDER BY weekdayIndex ASC, hourIndex ASC
            """)
    List<HeatmapStatRow> selectRecentHeatmap(@Param("start") Timestamp start,
                                             @Param("end") Timestamp end);

    @Select("""
                SELECT
                  m.id AS roomId,
                  m.room_code AS roomCode,
                  m.name AS roomName,
                  m.location AS location,
                  COUNT(r.id) AS reservationCount,
                  COALESCE(SUM(CASE WHEN r.status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS activeCount,
                  COALESCE(SUM(CASE WHEN r.status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelledCount
                FROM reservation r
                JOIN meeting_room m ON m.id = r.room_id
                WHERE r.start_time >= #{start}
                  AND r.start_time < #{end}
                GROUP BY m.id, m.room_code, m.name, m.location
                ORDER BY reservationCount DESC, m.room_code ASC, m.id ASC
                LIMIT 6
            """)
    List<RoomUsageRankRow> selectRoomUsageRanks(@Param("start") Timestamp start,
                                                @Param("end") Timestamp end);

    @Select("""
                SELECT
                  d.id AS deviceId,
                  d.device_code AS deviceCode,
                  d.name AS deviceName,
                  d.status AS status,
                  COALESCE(SUM(rd.quantity), 0) AS usageQuantity,
                  COUNT(DISTINCT rd.reservation_id) AS reservationCount
                FROM reservation_device rd
                JOIN reservation r ON r.id = rd.reservation_id
                JOIN device d ON d.id = rd.device_id
                WHERE r.start_time >= #{start}
                  AND r.start_time < #{end}
                GROUP BY d.id, d.device_code, d.name, d.status
                ORDER BY usageQuantity DESC, reservationCount DESC, d.device_code ASC, d.id ASC
                LIMIT 6
            """)
    List<DeviceUsageRankRow> selectDeviceUsageRanks(@Param("start") Timestamp start,
                                                    @Param("end") Timestamp end);

    @Select("""
                SELECT
                  m.id AS roomId,
                  m.name AS roomName
                FROM meeting_room m
                WHERE m.status = 'MAINTENANCE'
                ORDER BY m.room_code ASC, m.id ASC
            """)
    List<MaintenanceRoomAlertRow> selectMaintenanceRoomAlerts();

    @Select("""
                SELECT
                  m.id AS roomId,
                  m.name AS roomName
                FROM meeting_room m
                WHERE NOT EXISTS (
                  SELECT 1
                  FROM room_device rd
                  WHERE rd.room_id = m.id
                )
                ORDER BY m.room_code ASC, m.id ASC
            """)
    List<UnboundRoomAlertRow> selectUnboundRoomAlerts();

    @Select("""
                SELECT
                  d.id AS deviceId,
                  d.name AS deviceName
                FROM device d
                WHERE d.status = 'DISABLED'
                  AND EXISTS (
                    SELECT 1
                    FROM room_device rd
                    WHERE rd.device_id = d.id
                  )
                ORDER BY d.device_code ASC, d.id ASC
            """)
    List<DisabledBoundDeviceAlertRow> selectDisabledBoundDeviceAlerts();

    @Select("""
                SELECT
                  m.id AS roomId,
                  m.name AS roomName,
                  COUNT(r.id) AS reservationCount,
                  COALESCE(SUM(CASE WHEN r.status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelledCount
                FROM reservation r
                JOIN meeting_room m ON m.id = r.room_id
                WHERE r.start_time >= #{start}
                  AND r.start_time < #{end}
                GROUP BY m.id, m.name
                HAVING COUNT(r.id) >= 2
                   AND COALESCE(SUM(CASE WHEN r.status = 'CANCELLED' THEN 1 ELSE 0 END), 0) * 1.0 / COUNT(r.id) >= 0.4
                ORDER BY reservationCount DESC, cancelledCount DESC, m.id ASC
            """)
    List<HighCancelRoomAlertRow> selectHighCancelRoomAlerts(@Param("start") Timestamp start,
                                                            @Param("end") Timestamp end);

    @Select("""
                SELECT
                  r.id,
                  r.reservation_no AS reservationNo,
                  r.title,
                  m.name AS roomName,
                  u.display_name AS organizerName,
                  DATE_FORMAT(r.start_time, '%Y-%m-%d %H:%i:%s') AS startTime,
                  DATE_FORMAT(r.end_time, '%Y-%m-%d %H:%i:%s') AS endTime,
                  r.status,
                  COALESCE(
                    GROUP_CONCAT(
                      CONCAT(d.name, ' x', rd.quantity)
                      ORDER BY d.id SEPARATOR ' / '
                    ),
                    '未调用设备'
                  ) AS deviceSummary
                FROM reservation r
                JOIN meeting_room m ON m.id = r.room_id
                JOIN sys_user u ON u.id = r.organizer_id
                LEFT JOIN reservation_device rd ON rd.reservation_id = r.id
                LEFT JOIN device d ON d.id = rd.device_id
                WHERE r.start_time >= #{start}
                  AND r.start_time < #{end}
                GROUP BY r.id, r.reservation_no, r.title, m.name, u.display_name, r.start_time, r.end_time, r.status
                ORDER BY r.start_time DESC, r.id DESC
                LIMIT 8
            """)
    List<RecentReservationRow> selectRecentReservations(@Param("start") Timestamp start,
                                                        @Param("end") Timestamp end);

    class RecentTrendRow {
        public String date;
        public Long reservationCount;
    }

    class HeatmapStatRow {
        public Integer weekdayIndex;
        public Integer hourIndex;
        public Long reservationCount;
    }

    class RoomUsageRankRow {
        public Long roomId;
        public String roomCode;
        public String roomName;
        public String location;
        public Long reservationCount;
        public Long activeCount;
        public Long cancelledCount;
    }

    class DeviceUsageRankRow {
        public Long deviceId;
        public String deviceCode;
        public String deviceName;
        public String status;
        public Long usageQuantity;
        public Long reservationCount;
    }

    class MaintenanceRoomAlertRow {
        public Long roomId;
        public String roomName;
    }

    class UnboundRoomAlertRow {
        public Long roomId;
        public String roomName;
    }

    class DisabledBoundDeviceAlertRow {
        public Long deviceId;
        public String deviceName;
    }

    class HighCancelRoomAlertRow {
        public Long roomId;
        public String roomName;
        public Long reservationCount;
        public Long cancelledCount;
    }

    class RecentReservationRow {
        public Long id;
        public String reservationNo;
        public String title;
        public String roomName;
        public String organizerName;
        public String startTime;
        public String endTime;
        public String status;
        public String deviceSummary;
    }
}
