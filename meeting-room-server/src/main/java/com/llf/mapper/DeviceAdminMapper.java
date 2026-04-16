package com.llf.mapper;

import com.llf.vo.admin.device.AdminDeviceVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

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
