package com.llf.mapper;

import com.llf.vo.room.RoomListItemVO;
import com.llf.vo.room.RoomDeviceOptionVO;
import com.llf.vo.room.RoomOptionVO;
import com.llf.vo.room.RoomPageDeviceVO;
import com.llf.vo.room.RoomPageItemVO;
import lombok.Data;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RoomMapper {

    @Select("SELECT COUNT(*) FROM meeting_room")
    Integer countAll();

    @Select("SELECT COUNT(*) FROM meeting_room WHERE CONCAT(status, '') IN ('AVAILABLE', '1')")
    Integer countAvailable();

    @Select("SELECT COUNT(*) FROM meeting_room WHERE CONCAT(status, '') IN ('MAINTENANCE', '2')")
    Integer countMaintenance();

    @Select("""
            SELECT COUNT(*)
            FROM meeting_room r
            WHERE NOT EXISTS (
              SELECT 1
              FROM room_device rd
              WHERE rd.room_id = r.id
            )
            """)
    Integer countUnbound();

    @Select("SELECT COUNT(*) FROM meeting_room WHERE capacity >= 17")
    Integer countLarge();

    @Select("""
            <script>
              SELECT
                r.id,
                r.room_code AS roomCode,
                r.name,
                r.location,
                r.capacity,
                CASE CONCAT(r.status, '')
                  WHEN '1' THEN 'AVAILABLE'
                  WHEN '2' THEN 'MAINTENANCE'
                  ELSE CONCAT(r.status, '')
                END AS status
              FROM meeting_room r
              <where>
                <if test="keyword != null and keyword != ''">
                  AND (
                    r.name LIKE CONCAT('%', #{keyword}, '%')
                    OR r.location LIKE CONCAT('%', #{keyword}, '%')
                    OR r.room_code LIKE CONCAT('%', #{keyword}, '%')
                  )
                </if>
                <if test="onlyAvailable != null and onlyAvailable == true">
                  AND CONCAT(r.status, '') IN ('AVAILABLE', '1')
                </if>
                <if test="deviceId != null">
                  AND EXISTS (
                    SELECT 1 FROM room_device rd
                    WHERE rd.room_id = r.id AND rd.device_id = #{deviceId}
                  )
                </if>
              </where>
              ORDER BY r.name ASC, r.id ASC
            </script>
            """)
    List<RoomListItemVO> selectRooms(@Param("keyword") String keyword,
                                     @Param("deviceId") Long deviceId,
                                     @Param("onlyAvailable") Boolean onlyAvailable);

    @Select("""
            <script>
              SELECT COUNT(*)
              FROM meeting_room r
              <where>
                <if test="keyword != null and keyword != ''">
                  AND (
                    r.name LIKE CONCAT('%', #{keyword}, '%')
                    OR r.location LIKE CONCAT('%', #{keyword}, '%')
                    OR r.room_code LIKE CONCAT('%', #{keyword}, '%')
                  )
                </if>
                <if test="status != null and status != ''">
                  AND (
                    CONCAT(r.status, '') = #{status}
                    OR CONCAT(r.status, '') = CASE #{status}
                      WHEN 'AVAILABLE' THEN '1'
                      WHEN 'MAINTENANCE' THEN '2'
                      ELSE #{status}
                    END
                  )
                </if>
                <if test="location != null and location != ''">
                  AND r.location = #{location}
                </if>
                <if test="capacityType != null and capacityType != ''">
                  <choose>
                    <when test="capacityType == 'small'">
                      AND r.capacity &lt;= 8
                    </when>
                    <when test="capacityType == 'medium'">
                      AND r.capacity BETWEEN 9 AND 16
                    </when>
                    <when test="capacityType == 'large'">
                      AND r.capacity &gt;= 17
                    </when>
                  </choose>
                </if>
                <if test="deviceIds != null and deviceIds.size() > 0">
                  AND r.id IN (
                    SELECT rd.room_id
                    FROM room_device rd
                    JOIN device d ON d.id = rd.device_id
                    WHERE CONCAT(d.status, '') IN ('ENABLED', '1')
                      AND rd.device_id IN
                      <foreach collection="deviceIds" item="deviceId" open="(" separator="," close=")">
                        #{deviceId}
                      </foreach>
                    GROUP BY rd.room_id
                    HAVING COUNT(DISTINCT rd.device_id) = #{deviceCount}
                  )
                </if>
              </where>
            </script>
            """)
    Long countRoomsForPage(@Param("keyword") String keyword,
                           @Param("status") String status,
                           @Param("capacityType") String capacityType,
                           @Param("location") String location,
                           @Param("deviceIds") List<Long> deviceIds,
                           @Param("deviceCount") Integer deviceCount);

    @Select("""
            <script>
              SELECT
                r.id,
                r.room_code AS roomCode,
                r.name,
                r.location,
                r.capacity,
                CASE CONCAT(r.status, '')
                  WHEN '1' THEN 'AVAILABLE'
                  WHEN '2' THEN 'MAINTENANCE'
                  ELSE CONCAT(r.status, '')
                END AS status,
                r.description,
                r.maintenance_remark AS maintenanceRemark
              FROM meeting_room r
              <where>
                <if test="keyword != null and keyword != ''">
                  AND (
                    r.name LIKE CONCAT('%', #{keyword}, '%')
                    OR r.location LIKE CONCAT('%', #{keyword}, '%')
                    OR r.room_code LIKE CONCAT('%', #{keyword}, '%')
                  )
                </if>
                <if test="status != null and status != ''">
                  AND (
                    CONCAT(r.status, '') = #{status}
                    OR CONCAT(r.status, '') = CASE #{status}
                      WHEN 'AVAILABLE' THEN '1'
                      WHEN 'MAINTENANCE' THEN '2'
                      ELSE #{status}
                    END
                  )
                </if>
                <if test="location != null and location != ''">
                  AND r.location = #{location}
                </if>
                <if test="capacityType != null and capacityType != ''">
                  <choose>
                    <when test="capacityType == 'small'">
                      AND r.capacity &lt;= 8
                    </when>
                    <when test="capacityType == 'medium'">
                      AND r.capacity BETWEEN 9 AND 16
                    </when>
                    <when test="capacityType == 'large'">
                      AND r.capacity &gt;= 17
                    </when>
                  </choose>
                </if>
                <if test="deviceIds != null and deviceIds.size() > 0">
                  AND r.id IN (
                    SELECT rd.room_id
                    FROM room_device rd
                    JOIN device d ON d.id = rd.device_id
                    WHERE CONCAT(d.status, '') IN ('ENABLED', '1')
                      AND rd.device_id IN
                      <foreach collection="deviceIds" item="deviceId" open="(" separator="," close=")">
                        #{deviceId}
                      </foreach>
                    GROUP BY rd.room_id
                    HAVING COUNT(DISTINCT rd.device_id) = #{deviceCount}
                  )
                </if>
              </where>
              ORDER BY r.name ASC, r.id ASC
              LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<RoomPageItemVO> selectRoomPage(@Param("keyword") String keyword,
                                        @Param("status") String status,
                                        @Param("capacityType") String capacityType,
                                        @Param("location") String location,
                                        @Param("deviceIds") List<Long> deviceIds,
                                        @Param("deviceCount") Integer deviceCount,
                                        @Param("offset") Integer offset,
                                        @Param("limit") Integer limit);

    @Select("""
            SELECT DISTINCT
              d.id,
              d.name
            FROM room_device rd
            JOIN device d ON d.id = rd.device_id
            WHERE CONCAT(d.status, '') IN ('ENABLED', '1')
            ORDER BY d.name ASC, d.id ASC
            """)
    List<RoomDeviceOptionVO> selectRoomDeviceOptions();

    @Select("""
              SELECT d.name
              FROM room_device rd
              JOIN device d ON d.id = rd.device_id
              WHERE rd.room_id = #{roomId}
              ORDER BY d.id
            """)
    List<String> selectDeviceNamesByRoomId(@Param("roomId") Long roomId);

    @Select("""
              SELECT
                d.id,
                d.device_code AS deviceCode,
                d.name,
                rd.quantity AS quantity,
                d.total,
                CASE CONCAT(d.status, '')
                  WHEN '1' THEN 'ENABLED'
                  WHEN '0' THEN 'DISABLED'
                  ELSE CONCAT(d.status, '')
                END AS status
              FROM room_device rd
              JOIN device d ON d.id = rd.device_id
              WHERE rd.room_id = #{roomId}
              ORDER BY d.id
            """)
    List<RoomPageDeviceVO> selectDevicesByRoomId(@Param("roomId") Long roomId);

    @Select("""
                SELECT
                  id,
                  room_code AS roomCode,
                  name,
                  location,
                  capacity,
                  CASE CONCAT(status, '')
                    WHEN '1' THEN 'AVAILABLE'
                    WHEN '2' THEN 'MAINTENANCE'
                    ELSE CONCAT(status, '')
                  END AS status,
                  description
                FROM meeting_room
                WHERE CONCAT(status, '') IN ('AVAILABLE', '1')
                ORDER BY name ASC, id ASC
            """)
    List<RoomOptionVO> selectAvailableOptions();

    @Select("""
                SELECT
                  id,
                  room_code AS roomCode,
                  name,
                  location,
                  capacity
                FROM meeting_room
                WHERE CONCAT(status, '') IN ('AVAILABLE', '1')
                  AND capacity >= #{attendees}
                ORDER BY id ASC
            """)
    List<RecommendationRoomRow> selectRecommendationCandidates(@Param("attendees") Integer attendees);

    @Select("""
            <script>
              SELECT
                rd.room_id AS roomId,
                rd.device_id AS deviceId,
                rd.quantity AS quantity
              FROM room_device rd
              JOIN device d ON d.id = rd.device_id
              WHERE CONCAT(d.status, '') IN ('ENABLED', '1')
                AND rd.room_id IN
                <foreach collection="roomIds" item="roomId" open="(" separator="," close=")">
                  #{roomId}
                </foreach>
              ORDER BY rd.room_id ASC, rd.device_id ASC
            </script>
            """)
    List<RoomDeviceAvailabilityRow> selectEnabledRoomDevices(@Param("roomIds") List<Long> roomIds);

    @Select("""
                SELECT
                  id,
                  room_code AS roomCode,
                  name,
                  location,
                  capacity,
                  CASE CONCAT(status, '')
                    WHEN '1' THEN 'AVAILABLE'
                    WHEN '2' THEN 'MAINTENANCE'
                    ELSE CONCAT(status, '')
                  END AS status,
                  description
                FROM meeting_room
                WHERE id = #{id}
                LIMIT 1
            """)
    RoomOptionVO selectOptionById(@Param("id") Long id);

    @Select("""
                SELECT DISTINCT location
                FROM meeting_room
                WHERE location IS NOT NULL
                  AND location <> ''
                ORDER BY location
            """)
    List<String> selectLocations();

    @Select("SELECT id FROM meeting_room WHERE room_code = #{roomCode} LIMIT 1")
    Long selectIdByRoomCode(@Param("roomCode") String roomCode);

    @Select("""
            SELECT
              id,
              room_code AS roomCode,
              name,
              location,
              capacity,
              CASE CONCAT(status, '')
                WHEN '1' THEN 'AVAILABLE'
                WHEN '2' THEN 'MAINTENANCE'
                ELSE CONCAT(status, '')
              END AS status,
              description,
              maintenance_remark AS maintenanceRemark
            FROM meeting_room
            WHERE id = #{id}
            LIMIT 1
            """)
    RoomPageItemVO selectRoomById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM meeting_room WHERE room_code = #{roomCode}")
    int countByRoomCode(@Param("roomCode") String roomCode);

    @Select("SELECT COUNT(*) FROM meeting_room WHERE room_code = #{roomCode} AND id <> #{id}")
    int countByRoomCodeExcludeId(@Param("id") Long id, @Param("roomCode") String roomCode);

    @Insert("""
            INSERT INTO meeting_room(room_code, name, location, capacity, status, description, maintenance_remark, created_at, updated_at)
            VALUES(
              #{roomCode},
              #{name},
              #{location},
              #{capacity},
              CASE #{status}
                WHEN 'AVAILABLE' THEN 1
                WHEN 'MAINTENANCE' THEN 2
                ELSE #{status}
              END,
              #{description},
              #{maintenanceRemark},
              NOW(),
              NOW()
            )
            """)
    int insertRoom(@Param("roomCode") String roomCode,
                   @Param("name") String name,
                   @Param("location") String location,
                   @Param("capacity") Integer capacity,
                   @Param("status") String status,
                   @Param("description") String description,
                   @Param("maintenanceRemark") String maintenanceRemark);

    @Update("""
            UPDATE meeting_room
            SET room_code = #{roomCode},
                name = #{name},
                location = #{location},
                capacity = #{capacity},
                status = CASE #{status}
                    WHEN 'AVAILABLE' THEN 1
                    WHEN 'MAINTENANCE' THEN 2
                    ELSE #{status}
                END,
                description = #{description},
                maintenance_remark = #{maintenanceRemark},
                updated_at = NOW()
            WHERE id = #{id}
            """)
    int updateRoomById(@Param("id") Long id,
                       @Param("roomCode") String roomCode,
                       @Param("name") String name,
                       @Param("location") String location,
                       @Param("capacity") Integer capacity,
                       @Param("status") String status,
                       @Param("description") String description,
                       @Param("maintenanceRemark") String maintenanceRemark);

    @Update("""
            UPDATE meeting_room
            SET status = CASE #{status}
                    WHEN 'AVAILABLE' THEN 1
                    WHEN 'MAINTENANCE' THEN 2
                    ELSE #{status}
                END,
                maintenance_remark = #{maintenanceRemark},
                updated_at = NOW()
            WHERE id = #{id}
            """)
    int updateStatusById(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("maintenanceRemark") String maintenanceRemark);

    @Delete("DELETE FROM room_device WHERE room_id = #{roomId}")
    int deleteRoomDevices(@Param("roomId") Long roomId);

    @Insert("INSERT INTO room_device(room_id, device_id, quantity) VALUES(#{roomId}, #{deviceId}, #{quantity})")
    int insertRoomDeviceWithQuantity(@Param("roomId") Long roomId,
                                     @Param("deviceId") Long deviceId,
                                     @Param("quantity") Integer quantity);

    @Delete("DELETE FROM meeting_room WHERE id = #{id}")
    int deleteRoomById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM device
            WHERE id IN
            <foreach collection="deviceIds" item="deviceId" open="(" separator="," close=")">
              #{deviceId}
            </foreach>
            </script>
            """)
    int countExistingDevices(@Param("deviceIds") List<Long> deviceIds);

    @Select("SELECT COUNT(*) FROM reservation WHERE room_id = #{roomId}")
    int countReservationsByRoomId(@Param("roomId") Long roomId);

    @Data
    class RecommendationRoomRow {
        private Long id;
        private String roomCode;
        private String name;
        private String location;
        private Integer capacity;
    }

    @Data
    class RoomDeviceAvailabilityRow {
        private Long roomId;
        private Long deviceId;
        private Integer quantity;
    }
}
