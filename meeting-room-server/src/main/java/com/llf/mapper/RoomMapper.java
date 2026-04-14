package com.llf.mapper;

import com.llf.vo.RoomListItemVO;
import com.llf.vo.RoomOptionVO;
import com.llf.vo.RoomPageDeviceVO;
import com.llf.vo.RoomPageItemVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RoomMapper {

    @Select("SELECT COUNT(*) FROM meeting_room")
    Integer countAll();

    @Select("SELECT COUNT(*) FROM meeting_room WHERE status = 'AVAILABLE'")
    Integer countAvailable();

    @Select("SELECT COUNT(*) FROM meeting_room WHERE status = 'MAINTENANCE'")
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
                r.status
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
                  AND r.status = 'AVAILABLE'
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
                  AND r.status = #{status}
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
              </where>
            </script>
            """)
    Long countRoomsForPage(@Param("keyword") String keyword,
                           @Param("status") String status,
                           @Param("capacityType") String capacityType,
                           @Param("location") String location);

    @Select("""
            <script>
              SELECT
                r.id,
                r.room_code AS roomCode,
                r.name,
                r.location,
                r.capacity,
                r.status,
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
                  AND r.status = #{status}
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
              </where>
              ORDER BY r.name ASC, r.id ASC
              LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<RoomPageItemVO> selectRoomPage(@Param("keyword") String keyword,
                                        @Param("status") String status,
                                        @Param("capacityType") String capacityType,
                                        @Param("location") String location,
                                        @Param("offset") Integer offset,
                                        @Param("limit") Integer limit);

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
                d.status
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
                  status,
                  description
                FROM meeting_room
                ORDER BY name ASC, id ASC
            """)
    List<RoomOptionVO> selectOptions();

    @Select("""
                SELECT
                  id,
                  room_code AS roomCode,
                  name,
                  location,
                  capacity,
                  status,
                  description
                FROM meeting_room
                WHERE status = 'AVAILABLE'
                ORDER BY name ASC, id ASC
            """)
    List<RoomOptionVO> selectAvailableOptions();

    @Select("""
                SELECT
                  id,
                  room_code AS roomCode,
                  name,
                  location,
                  capacity,
                  status,
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
              status,
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
            VALUES(#{roomCode}, #{name}, #{location}, #{capacity}, #{status}, #{description}, #{maintenanceRemark}, NOW(), NOW())
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
            SET name = #{name},
                location = #{location},
                capacity = #{capacity},
                status = #{status},
                description = #{description},
                maintenance_remark = #{maintenanceRemark},
                updated_at = NOW()
            WHERE room_code = #{roomCode}
            """)
    int updateRoomByCode(@Param("roomCode") String roomCode,
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
                status = #{status},
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
            SET status = #{status},
                maintenance_remark = #{maintenanceRemark},
                updated_at = NOW()
            WHERE id = #{id}
            """)
    int updateStatusById(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("maintenanceRemark") String maintenanceRemark);

    @Delete("DELETE FROM room_device WHERE room_id = #{roomId}")
    int deleteRoomDevices(@Param("roomId") Long roomId);

    @Insert("INSERT INTO room_device(room_id, device_id, quantity) VALUES(#{roomId}, #{deviceId}, 1)")
    int insertRoomDevice(@Param("roomId") Long roomId, @Param("deviceId") Long deviceId);

    @Insert("INSERT INTO room_device(room_id, device_id, quantity) VALUES(#{roomId}, #{deviceId}, #{quantity})")
    int insertRoomDeviceWithQuantity(@Param("roomId") Long roomId,
                                     @Param("deviceId") Long deviceId,
                                     @Param("quantity") Integer quantity);

    @Delete("DELETE FROM meeting_room WHERE room_code = #{roomCode}")
    int deleteRoomByCode(@Param("roomCode") String roomCode);

    @Delete("DELETE FROM meeting_room WHERE id = #{id}")
    int deleteRoomById(@Param("id") Long id);

    @Select("""
            SELECT d.id
            FROM room_device rd
            JOIN device d ON rd.device_id = d.id
            WHERE rd.room_id = #{roomId}
            ORDER BY d.id
            """)
    List<Long> selectDeviceIdsByRoomId(@Param("roomId") Long roomId);

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
}
