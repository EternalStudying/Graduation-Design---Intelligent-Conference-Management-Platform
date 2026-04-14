package com.llf.mapper;

import com.llf.vo.AdminDeviceVO;
import com.llf.vo.DeviceAdminVO;
import com.llf.vo.ReservationBriefVO;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface DeviceAdminMapper {

    @Select("""
            SELECT
              id,
              room_code AS roomCode,
              name AS roomName,
              location,
              status AS roomStatus
            FROM meeting_room
            ORDER BY room_code ASC, id ASC
            """)
    List<BindingRoomRow> selectBindingRooms();

    @Select("""
            SELECT
              id,
              device_code AS deviceCode,
              name,
              total,
              status
            FROM device
            ORDER BY device_code ASC, id ASC
            """)
    List<BindingDeviceRow> selectBindingDevices();

    @Select("""
            SELECT
              rd.room_id AS roomId,
              rd.device_id AS deviceId,
              m.room_code AS roomCode,
              m.name AS roomName,
              m.location AS location,
              m.status AS roomStatus,
              d.device_code AS deviceCode,
              d.name AS deviceName,
              d.total AS deviceTotal,
              d.status AS deviceStatus
            FROM room_device rd
            JOIN meeting_room m ON m.id = rd.room_id
            JOIN device d ON d.id = rd.device_id
            ORDER BY d.device_code ASC, d.id ASC, m.room_code ASC, m.id ASC
            """)
    List<DeviceBindingRelationRow> selectBindingRelations();

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM device d
            WHERE 1=1
              <if test="keyword != null and keyword != ''">
                AND (
                  LOWER(d.device_code) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                  OR LOWER(d.name) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                )
              </if>
              <if test="status != null and status != ''">
                AND d.status = #{status}
              </if>
            </script>
            """)
    Long countAdminPage(@Param("keyword") String keyword,
                        @Param("status") String status);

    @Select("""
            <script>
            SELECT
              d.id,
              d.device_code AS deviceCode,
              d.name,
              d.total,
              d.status,
              COALESCE(rb.boundRoomCount, 0) AS boundRoomCount,
              COALESCE(rb.boundQuantity, 0) AS boundQuantity,
              GREATEST(d.total - COALESCE(rb.boundQuantity, 0), 0) AS availableQuantity
            FROM device d
            LEFT JOIN (
              SELECT
                rd.device_id AS deviceId,
                COUNT(DISTINCT rd.room_id) AS boundRoomCount,
                COALESCE(SUM(rd.quantity), 0) AS boundQuantity
              FROM room_device rd
              GROUP BY rd.device_id
            ) rb ON rb.deviceId = d.id
            WHERE 1=1
              <if test="keyword != null and keyword != ''">
                AND (
                  LOWER(d.device_code) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                  OR LOWER(d.name) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                )
              </if>
              <if test="status != null and status != ''">
                AND d.status = #{status}
              </if>
            ORDER BY d.device_code ASC, d.id ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminDeviceVO> selectAdminPage(@Param("keyword") String keyword,
                                        @Param("status") String status,
                                        @Param("offset") Integer offset,
                                        @Param("limit") Integer limit);

    @Select("""
            SELECT
              d.id,
              d.device_code AS deviceCode,
              d.name,
              d.total,
              d.status,
              COALESCE(rb.boundRoomCount, 0) AS boundRoomCount,
              COALESCE(rb.boundQuantity, 0) AS boundQuantity,
              GREATEST(d.total - COALESCE(rb.boundQuantity, 0), 0) AS availableQuantity
            FROM device d
            LEFT JOIN (
              SELECT
                rd.device_id AS deviceId,
                COUNT(DISTINCT rd.room_id) AS boundRoomCount,
                COALESCE(SUM(rd.quantity), 0) AS boundQuantity
              FROM room_device rd
              GROUP BY rd.device_id
            ) rb ON rb.deviceId = d.id
            WHERE d.id = #{id}
            LIMIT 1
            """)
    AdminDeviceVO selectAdminDetailById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT
              rd.device_id AS deviceId,
              m.id AS roomId,
              m.room_code AS roomCode,
              m.name AS roomName,
              m.location AS location,
              rd.quantity AS quantity
            FROM room_device rd
            JOIN meeting_room m ON m.id = rd.room_id
            WHERE rd.device_id IN
            <foreach collection="deviceIds" item="deviceId" open="(" separator="," close=")">
              #{deviceId}
            </foreach>
            ORDER BY rd.device_id ASC, m.room_code ASC, m.id ASC
            </script>
            """)
    List<DeviceBoundRoomRow> selectBoundRoomsByDeviceIds(@Param("deviceIds") List<Long> deviceIds);

    @Select("SELECT COUNT(*) FROM device")
    Integer countAll();

    @Select("SELECT COUNT(*) FROM device WHERE status = 'ENABLED'")
    Integer countEnabled();

    @Select("SELECT COUNT(*) FROM device WHERE status = 'DISABLED'")
    Integer countDisabled();

    @Select("""
            SELECT COUNT(*)
            FROM device d
            LEFT JOIN (
              SELECT device_id, COALESCE(SUM(quantity), 0) AS boundQuantity
              FROM room_device
              GROUP BY device_id
            ) rd ON rd.device_id = d.id
            WHERE GREATEST(d.total - COALESCE(rd.boundQuantity, 0), 0) <= 1
            """)
    Integer countWarning();

    @Select("SELECT COUNT(1) FROM device WHERE device_code = #{deviceCode}")
    int countByDeviceCode(@Param("deviceCode") String deviceCode);

    @Select("SELECT COUNT(1) FROM device WHERE device_code = #{deviceCode} AND id <> #{id}")
    int countByDeviceCodeExcludeId(@Param("id") Long id, @Param("deviceCode") String deviceCode);

    @Select("SELECT id FROM device WHERE device_code = #{code} LIMIT 1")
    Long selectIdByCode(@Param("code") String code);

    @Select("SELECT COUNT(1) FROM room_device WHERE device_id = #{deviceId}")
    int countRoomBindings(@Param("deviceId") Long deviceId);

    @Select("SELECT COALESCE(SUM(quantity), 0) FROM room_device WHERE device_id = #{deviceId}")
    Integer sumBoundQuantity(@Param("deviceId") Long deviceId);

    @Insert("""
            INSERT INTO device(device_code, name, total, status, created_at)
            VALUES(#{deviceCode}, #{name}, #{total}, #{status}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertAdminDevice(AdminDeviceCreateRow row);

    @Update("""
            UPDATE device
            SET device_code = #{deviceCode},
                name = #{name},
                total = #{total},
                status = #{status}
            WHERE id = #{id}
            """)
    int updateById(AdminDeviceUpdateRow row);

    @Update("""
            UPDATE device
            SET status = #{status}
            WHERE id = #{id}
            """)
    int updateStatusById(@Param("id") Long id, @Param("status") String status);

    @Delete("DELETE FROM device WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT
              d.device_code AS code,
              d.name,
              d.total,
              d.status,
              d.description,
              (
                SELECT COUNT(1)
                FROM room_device rd
                WHERE rd.device_id = d.id
              ) AS usedCount
            FROM device d
            WHERE 1=1
              <if test="keyword != null and keyword != ''">
                AND (
                  LOWER(d.device_code) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                  OR LOWER(d.name) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                )
              </if>
              <if test="status != null and status != ''">
                AND d.status = #{status}
              </if>
            ORDER BY d.device_code
            </script>
            """)
    List<DeviceAdminVO> selectAdminList(@Param("keyword") String keyword,
                                        @Param("status") String status);

    @Select("SELECT COUNT(1) FROM device WHERE device_code = #{code}")
    int existsByCode(@Param("code") String code);

    @Select("SELECT COUNT(1) FROM device WHERE name = #{name}")
    int existsByName(@Param("name") String name);

    @Select("""
            SELECT COUNT(1)
            FROM device
            WHERE name = #{name}
              AND device_code <> #{code}
            """)
    int existsByNameExcludeCode(@Param("name") String name, @Param("code") String code);

    @Insert("""
            INSERT INTO device(device_code, name, total, status, description, created_at)
            VALUES(#{code}, #{name}, #{total}, #{status}, #{description}, NOW())
            """)
    int insert(@Param("code") String code,
               @Param("name") String name,
               @Param("total") Integer total,
               @Param("status") String status,
               @Param("description") String description);

    @Update("""
            UPDATE device
            SET name=#{name},
                total=#{total},
                status=#{status},
                description=#{description}
            WHERE device_code=#{code}
            """)
    int updateByCode(@Param("code") String code,
                     @Param("name") String name,
                     @Param("total") Integer total,
                     @Param("status") String status,
                     @Param("description") String description);

    @Delete("DELETE FROM room_device WHERE device_id = #{deviceId}")
    int deleteRoomBindings(@Param("deviceId") Long deviceId);

    @Delete("DELETE FROM device WHERE device_code = #{code}")
    int deleteByCode(@Param("code") String code);

    @Select("""
            SELECT
              d.id,
              d.device_code,
              d.name,
              d.status,
              d.total
            FROM device d
            WHERE d.status = 'ENABLED'
            ORDER BY d.device_code
            """)
    List<Map<String, Object>> selectAllEnabledDevices();

    @Select("""
            SELECT r.start_time, r.end_time, rd.quantity
            FROM reservation r
            JOIN reservation_device rd ON rd.reservation_id = r.id
            WHERE r.status = 'active'
              AND rd.device_id = #{deviceId}
            """)
    List<Map<String, Object>> selectActiveTimeRangesByDeviceId(@Param("deviceId") Long deviceId);

    @Select("""
            SELECT id, device_code, name, total, status
            FROM device
            WHERE device_code = #{code}
            LIMIT 1
            """)
    Map<String, Object> selectDeviceByCode(@Param("code") String code);

    @Select("SELECT COUNT(1) FROM meeting_room")
    int countRooms();

    @Select("""
            SELECT COUNT(1)
            FROM room_device rd
            WHERE rd.device_id = #{deviceId}
            """)
    int countRoomsBoundToDevice(@Param("deviceId") Long deviceId);

    @Select("""
            SELECT r.start_time, r.end_time, rd.quantity
            FROM reservation r
            JOIN reservation_device rd ON rd.reservation_id = r.id
            JOIN device d ON d.id = rd.device_id
            WHERE r.status = 'active'
              AND d.device_code = #{deviceCode}
            """)
    List<Map<String, Object>> selectActiveTimeRangesByDeviceCode(@Param("deviceCode") String deviceCode);

    @Select("""
            SELECT r.id, r.title, mr.room_code AS roomId,
                   r.start_time AS start,
                   r.end_time AS end
            FROM reservation r
            join meeting_room mr on r.room_id = mr.id
            JOIN reservation_device rd ON rd.reservation_id = r.id
            JOIN device d ON d.id = rd.device_id
            WHERE r.status = 'active'
              AND d.device_code = #{deviceCode}
            ORDER BY r.start_time DESC
            """)
    List<ReservationBriefVO> selectActiveReservationBriefsByDeviceCode(@Param("deviceCode") String deviceCode);

    class AdminDeviceCreateRow {
        public Long id;
        public String deviceCode;
        public String name;
        public Integer total;
        public String status;
    }

    class AdminDeviceUpdateRow {
        public Long id;
        public String deviceCode;
        public String name;
        public Integer total;
        public String status;
    }

    class DeviceBoundRoomRow {
        public Long deviceId;
        public Long roomId;
        public String roomCode;
        public String roomName;
        public String location;
        public Integer quantity;
    }

    class BindingRoomRow {
        public Long id;
        public String roomCode;
        public String roomName;
        public String location;
        public String roomStatus;
    }

    class BindingDeviceRow {
        public Long id;
        public String deviceCode;
        public String name;
        public Integer total;
        public String status;
    }

    class DeviceBindingRelationRow {
        public Long roomId;
        public Long deviceId;
        public String roomCode;
        public String roomName;
        public String location;
        public String roomStatus;
        public String deviceCode;
        public String deviceName;
        public Integer deviceTotal;
        public String deviceStatus;
    }
}
