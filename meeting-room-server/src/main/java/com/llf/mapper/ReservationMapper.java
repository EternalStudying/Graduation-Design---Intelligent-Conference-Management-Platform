package com.llf.mapper;

import com.llf.vo.CalendarEventVO;
import com.llf.vo.DashboardReservationSlotVO;
import com.llf.vo.DashboardTodayScheduleVO;
import com.llf.vo.MyReservationVO;
import com.llf.vo.ReservationCreateVO;
import org.apache.ibatis.annotations.*;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface ReservationMapper {

    @Select("SELECT COUNT(*) FROM reservation WHERE status = 'ACTIVE'")
    Integer countActive();

    @Select("""
            SELECT COUNT(*)
            FROM reservation
            WHERE status <> 'CANCELLED'
              AND start_time < #{end}
              AND end_time > #{start}
            """)
    Integer countTodayMeetings(@Param("start") Timestamp start,
                               @Param("end") Timestamp end);

    @Select("""
            SELECT COUNT(*)
            FROM reservation
            WHERE organizer_id = #{organizerId}
              AND status = 'ACTIVE'
              AND end_time >= #{now}
            """)
    Integer countPendingByOrganizerId(@Param("organizerId") Long organizerId,
                                      @Param("now") Timestamp now);

    @Select("""
            SELECT
              r.id,
              r.organizer_id AS organizerId,
              r.room_id AS roomId,
              m.name AS roomName,
              r.title,
              r.start_time AS startTime,
              r.end_time AS endTime,
              r.attendees,
              r.status
            FROM reservation r
            JOIN meeting_room m ON m.id = r.room_id
            WHERE r.status <> 'CANCELLED'
              AND r.start_time < #{end}
              AND r.end_time > #{start}
            ORDER BY r.start_time ASC, r.id ASC
            """)
    List<DashboardReservationSlotVO> selectTodayOverviewReservations(@Param("start") Timestamp start,
                                                                     @Param("end") Timestamp end);

    @Select("""
            SELECT
              r.id,
              r.start_time AS startTime,
              r.end_time AS endTime,
              r.title,
              r.room_id AS roomId,
              m.name AS roomName,
              r.attendees,
              r.status,
              COALESCE(
                GROUP_CONCAT(
                  DISTINCT CASE
                    WHEN rd.quantity > 1 THEN CONCAT(d.name, 'x', rd.quantity)
                    ELSE d.name
                  END
                  ORDER BY d.id SEPARATOR '、'
                ),
                ''
              ) AS deviceSummary
            FROM reservation r
            JOIN meeting_room m ON m.id = r.room_id
            LEFT JOIN reservation_device rd ON rd.reservation_id = r.id
            LEFT JOIN device d ON d.id = rd.device_id
            WHERE r.organizer_id = #{organizerId}
              AND r.status <> 'CANCELLED'
              AND r.start_time < #{end}
              AND r.end_time > #{start}
            GROUP BY r.id, r.start_time, r.end_time, r.title, r.room_id, m.name, r.attendees, r.status
            ORDER BY r.start_time ASC, r.id ASC
            """)
    List<DashboardTodayScheduleVO> selectTodaySchedulesByOrganizerId(@Param("organizerId") Long organizerId,
                                                                     @Param("start") Timestamp start,
                                                                     @Param("end") Timestamp end);

    @Select("""
            <script>
            SELECT
              r.id,
              r.reservation_no AS reservationNo,
              r.room_id AS roomId,
              m.name AS roomName,
              r.organizer_id AS organizerId,
              u.display_name AS organizerName,
              r.title,
              r.attendees,
              DATE_FORMAT(r.start_time, '%Y-%m-%d %H:%i:%s') AS startTime,
              DATE_FORMAT(r.end_time, '%Y-%m-%d %H:%i:%s') AS endTime,
              r.status,
              r.cancel_reason AS cancelReason
            FROM reservation r
            JOIN meeting_room m ON m.id = r.room_id
            JOIN sys_user u ON u.id = r.organizer_id
            WHERE r.start_time &lt; #{end}
              AND r.end_time &gt; #{start}
              <if test="roomId != null">
                AND r.room_id = #{roomId}
              </if>
              <if test="status != null and status != ''">
                AND r.status = #{status}
              </if>
            ORDER BY r.start_time ASC, r.id ASC
            </script>
            """)
    List<CalendarEventVO> selectCalendarEvents(@Param("start") Timestamp start,
                                               @Param("end") Timestamp end,
                                               @Param("roomId") Long roomId,
                                               @Param("status") String status);

    @Select("""
            <script>
            SELECT
              rd.reservation_id AS reservationId,
              rd.device_id AS id,
              rd.device_id AS deviceId,
              d.device_code AS deviceCode,
              d.name AS name,
              rd.quantity AS quantity,
              d.status AS status
            FROM reservation_device rd
            JOIN device d ON d.id = rd.device_id
            WHERE rd.reservation_id IN
            <foreach collection="reservationIds" item="reservationId" open="(" separator="," close=")">
              #{reservationId}
            </foreach>
            ORDER BY rd.reservation_id ASC, rd.device_id ASC
            </script>
            """)
    List<CalendarEventVO.DeviceRow> selectCalendarEventDevices(@Param("reservationIds") List<Long> reservationIds);

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
            WHERE id = #{roomId}
            LIMIT 1
            """)
    RoomRow selectRoomById(@Param("roomId") Long roomId);

    // ✅ 插入（含 reservation_no）
    @Insert("""
            INSERT INTO reservation(
              reservation_no,
              room_id,
              organizer_id,
              title,
              remark,
              attendees,
              start_time,
              end_time,
              status,
              created_at,
              updated_at
            )
            VALUES(
              #{reservationNo},
              #{roomId},
              #{organizerId},
              #{title},
              #{remark},
              #{attendees},
              #{startTime},
              #{endTime},
              'ACTIVE',
              NOW(),
              NOW()
            )
            """)
    int insertReservation(@Param("reservationNo") String reservationNo,
                          @Param("roomId") Long roomId,
                          @Param("organizerId") Long organizerId,
                          @Param("title") String title,
                          @Param("remark") String remark,
                          @Param("attendees") Integer attendees,
                          @Param("startTime") Timestamp startTime,
                          @Param("endTime") Timestamp endTime);


    // ✅ 拿到刚插入的自增 id（MySQL）
    @Select("SELECT LAST_INSERT_ID()")
    Long lastInsertId();

    @Update("""
              UPDATE reservation
              SET status='CANCELLED',
                  cancel_reason = IFNULL(cancel_reason, '用户取消')
              WHERE id=#{id}
            """)
    int cancel(@Param("id") Long id);

    @Select("""
            SELECT COUNT(1)
            FROM reservation r
            WHERE r.room_id = #{roomId}
              AND r.status = 'ACTIVE'
              AND r.start_time < #{end}
              AND r.end_time > #{start}
            """)
    int countConflictByRoomId(@Param("roomId") Long roomId,
                              @Param("start") Timestamp start,
                              @Param("end") Timestamp end);

    @Select("""
            SELECT
                id,
                reservation_no AS reservationNo,
                room_id AS roomId,
                organizer_id AS organizerId,
                title,
                attendees,
                DATE_FORMAT(start_time, '%Y-%m-%d %H:%i:%s') AS startTime,
                DATE_FORMAT(end_time, '%Y-%m-%d %H:%i:%s') AS endTime,
                status,
                remark
            FROM reservation
            WHERE id = #{id}
            LIMIT 1
            """)
    ReservationCreateVO selectCreateResultById(@Param("id") Long id);

    @Select("""
            <script>
                SELECT
                    r.id,
                    r.reservation_no AS reservationNo,
                    r.room_id AS roomId,
                    m.room_code AS roomCode,
                    m.name AS roomName,
                    m.location AS roomLocation,
                    m.capacity AS roomCapacity,
                    m.description AS roomDescription,
                    r.organizer_id AS organizerId,
                    u.display_name AS organizerName,
                    r.title,
                    r.attendees,
                    DATE_FORMAT(r.start_time, '%Y-%m-%d %H:%i:%s') AS startTime,
                    DATE_FORMAT(r.end_time, '%Y-%m-%d %H:%i:%s') AS endTime,
                    r.status,
                    r.remark,
                    r.cancel_reason AS cancelReason,
                    CASE
                        WHEN r.organizer_id = #{currentUserId} THEN 'ORGANIZER'
                        ELSE 'PARTICIPANT'
                    END AS role,
                    CASE
                        WHEN r.organizer_id = #{currentUserId}
                             AND r.status = 'ACTIVE'
                             AND r.end_time &gt; NOW()
                        THEN TRUE ELSE FALSE
                    END AS canEdit,
                    CASE
                        WHEN r.organizer_id = #{currentUserId}
                             AND r.status = 'ACTIVE'
                             AND r.end_time &gt; NOW()
                        THEN TRUE ELSE FALSE
                    END AS canCancel
                FROM reservation r
                JOIN meeting_room m ON m.id = r.room_id
                JOIN sys_user u ON u.id = r.organizer_id
                WHERE r.start_time &gt;= #{start}
                  AND r.start_time &lt; #{end}
                  <if test="status != null and status != ''">
                    AND r.status = #{status}
                  </if>
                  <choose>
                    <when test="scope == 'organizer'">
                      AND r.organizer_id = #{currentUserId}
                    </when>
                    <when test="scope == 'participant'">
                      AND r.organizer_id &lt;&gt; #{currentUserId}
                      AND EXISTS (
                        SELECT 1
                        FROM reservation_participant rp
                        WHERE rp.reservation_id = r.id
                          AND rp.user_id = #{currentUserId}
                      )
                    </when>
                    <otherwise>
                      AND (
                        r.organizer_id = #{currentUserId}
                        OR EXISTS (
                          SELECT 1
                          FROM reservation_participant rp
                          WHERE rp.reservation_id = r.id
                            AND rp.user_id = #{currentUserId}
                        )
                      )
                    </otherwise>
                  </choose>
                ORDER BY r.start_time ASC, r.id ASC
            </script>
            """)
    List<MyReservationVO> selectMyReservations(@Param("currentUserId") Long currentUserId,
                                               @Param("start") Timestamp start,
                                               @Param("end") Timestamp end,
                                               @Param("scope") String scope,
                                               @Param("status") String status);

    @Select("""
            <script>
            SELECT
              rd.reservation_id AS reservationId,
              rd.device_id AS id,
              rd.device_id AS deviceId,
              d.device_code AS deviceCode,
              d.name,
              rd.quantity,
              d.status
            FROM reservation_device rd
            JOIN device d ON d.id = rd.device_id
            WHERE rd.reservation_id IN
            <foreach collection="reservationIds" item="reservationId" open="(" separator="," close=")">
              #{reservationId}
            </foreach>
            ORDER BY rd.reservation_id ASC, rd.device_id ASC
            </script>
            """)
    List<MyReservationVO.DeviceRow> selectMyReservationDevices(@Param("reservationIds") List<Long> reservationIds);

    @Select("""
            SELECT
                id,
                organizer_id AS organizerId,
                room_id AS roomId,
                status,
                end_time AS endTime
            FROM reservation
            WHERE id = #{id}
              AND organizer_id = #{organizerId}
            LIMIT 1
            """)
    ReservationEditableRow selectEditableReservation(@Param("id") Long id,
                                                     @Param("organizerId") Long organizerId);

    @Select("""
            SELECT COUNT(1)
            FROM reservation
            WHERE room_id = #{roomId}
              AND id &lt;&gt; #{reservationId}
              AND status = 'ACTIVE'
              AND start_time &lt; #{end}
              AND end_time &gt; #{start}
            """)
    int countConflictExcludeSelf(@Param("reservationId") Long reservationId,
                                 @Param("roomId") Long roomId,
                                 @Param("start") Timestamp start,
                                 @Param("end") Timestamp end);

    @Update("""
            UPDATE reservation
            SET title = #{title},
                room_id = #{roomId},
                start_time = #{startTime},
                end_time = #{endTime},
                attendees = #{attendees},
                remark = #{remark}
            WHERE id = #{id}
            """)
    int updateMyReservation(@Param("id") Long id,
                            @Param("title") String title,
                            @Param("roomId") Long roomId,
                            @Param("startTime") Timestamp startTime,
                            @Param("endTime") Timestamp endTime,
                            @Param("attendees") Integer attendees,
                            @Param("remark") String remark);

    @Update("""
            UPDATE reservation
            SET status = 'CANCELLED',
                cancel_reason = #{cancelReason}
            WHERE id = #{id}
            """)
    int cancelMyReservation(@Param("id") Long id,
                            @Param("cancelReason") String cancelReason);

    @Select("""
            SELECT
                r.id,
                r.reservation_no AS reservationNo,
                r.room_id AS roomId,
                m.room_code AS roomCode,
                m.name AS roomName,
                m.location AS roomLocation,
                m.capacity AS roomCapacity,
                m.description AS roomDescription,
                r.organizer_id AS organizerId,
                u.display_name AS organizerName,
                r.title,
                r.attendees,
                DATE_FORMAT(r.start_time, '%Y-%m-%d %H:%i:%s') AS startTime,
                DATE_FORMAT(r.end_time, '%Y-%m-%d %H:%i:%s') AS endTime,
                r.status,
                r.remark,
                r.cancel_reason AS cancelReason,
                'ORGANIZER' AS role,
                CASE
                    WHEN r.status = 'ACTIVE' AND r.end_time > NOW()
                    THEN TRUE ELSE FALSE
                END AS canEdit,
                CASE
                    WHEN r.status = 'ACTIVE' AND r.end_time > NOW()
                    THEN TRUE ELSE FALSE
                END AS canCancel
            FROM reservation r
            JOIN meeting_room m ON m.id = r.room_id
            JOIN sys_user u ON u.id = r.organizer_id
            WHERE r.id = #{id}
              AND r.organizer_id = #{currentUserId}
            LIMIT 1
            """)
    MyReservationVO selectMyReservationDetail(@Param("id") Long id,
                                              @Param("currentUserId") Long currentUserId);

    @Update("""
                UPDATE reservation
                SET status = 'ENDED'
                WHERE status = 'ACTIVE'
                  AND end_time < NOW()
            """)
    int markEnded();

    class ReservationEditableRow {
        public Long id;
        public Long organizerId;
        public Long roomId;
        public String status;
        public Timestamp endTime;
    }

    class RoomRow {
        public Long id;
        public String roomCode;
        public String name;
        public String location;
        public Integer capacity;
        public String status;
        public String description;
    }
}
