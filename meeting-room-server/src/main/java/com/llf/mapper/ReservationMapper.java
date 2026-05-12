package com.llf.mapper;

import com.llf.vo.reservation.CalendarEventVO;
import com.llf.vo.dashboard.DashboardReservationSlotVO;
import com.llf.vo.dashboard.DashboardTodayScheduleVO;
import com.llf.vo.admin.reservation.AdminReservationItemVO;
import com.llf.vo.reservation.MyReservationVO;
import com.llf.vo.reservation.ReservationCreateVO;
import lombok.Data;
import org.apache.ibatis.annotations.*;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface ReservationMapper {

    @Select("""
            SELECT COUNT(*)
            FROM reservation
            WHERE organizer_id = #{organizerId}
              AND CONCAT(status, '') IN ('PENDING', '1')
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
              CASE CONCAT(r.status, '')
                WHEN '1' THEN 'PENDING'
                WHEN '2' THEN 'ACTIVE'
                WHEN '3' THEN 'ENDED'
                WHEN '4' THEN 'CANCELLED'
                WHEN '5' THEN 'REJECTED'
                WHEN '6' THEN 'EXCEPTION'
                ELSE CONCAT(r.status, '')
              END AS status
            FROM reservation r
            JOIN meeting_room m ON m.id = r.room_id
            WHERE CONCAT(r.status, '') IN ('ACTIVE', '2')
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
              CASE CONCAT(r.status, '')
                WHEN '1' THEN 'PENDING'
                WHEN '2' THEN 'ACTIVE'
                WHEN '3' THEN 'ENDED'
                WHEN '4' THEN 'CANCELLED'
                WHEN '5' THEN 'REJECTED'
                WHEN '6' THEN 'EXCEPTION'
                ELSE CONCAT(r.status, '')
              END AS status,
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
              AND CONCAT(r.status, '') NOT IN ('CANCELLED', '4')
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
              CASE CONCAT(r.status, '')
                WHEN '1' THEN 'PENDING'
                WHEN '2' THEN 'ACTIVE'
                WHEN '3' THEN 'ENDED'
                WHEN '4' THEN 'CANCELLED'
                WHEN '5' THEN 'REJECTED'
                WHEN '6' THEN 'EXCEPTION'
                ELSE CONCAT(r.status, '')
              END AS status,
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
                AND (
                  CONCAT(r.status, '') = #{status}
                  OR CONCAT(r.status, '') = CASE #{status}
                    WHEN 'PENDING' THEN '1'
                    WHEN 'ACTIVE' THEN '2'
                    WHEN 'ENDED' THEN '3'
                    WHEN 'CANCELLED' THEN '4'
                    WHEN 'REJECTED' THEN '5'
                    WHEN 'EXCEPTION' THEN '6'
                    ELSE #{status}
                  END
                )
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
              CASE CONCAT(d.status, '')
                WHEN '1' THEN 'ENABLED'
                WHEN '0' THEN 'DISABLED'
                ELSE CONCAT(d.status, '')
              END AS status
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
              CASE CONCAT(status, '')
                WHEN '1' THEN 'AVAILABLE'
                WHEN '2' THEN 'MAINTENANCE'
                ELSE CONCAT(status, '')
              END AS status,
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
              1,
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

    @Insert("""
            INSERT INTO reservation_device(reservation_id, device_id, quantity)
            VALUES(#{reservationId}, #{deviceId}, #{quantity})
            """)
    int insertReservationDevice(@Param("reservationId") Long reservationId,
                                @Param("deviceId") Long deviceId,
                                @Param("quantity") Integer quantity);

    @Insert("""
            INSERT INTO reservation_participant(reservation_id, user_id, created_at)
            VALUES(#{reservationId}, #{userId}, NOW())
            """)
    int insertReservationParticipant(@Param("reservationId") Long reservationId,
                                     @Param("userId") Long userId);

    @Delete("""
            DELETE FROM reservation_participant
            WHERE reservation_id = #{reservationId}
            """)
    int deleteReservationParticipantsByReservationId(@Param("reservationId") Long reservationId);

    @Delete("""
            DELETE FROM reservation_device
            WHERE reservation_id = #{reservationId}
            """)
    int deleteReservationDevicesByReservationId(@Param("reservationId") Long reservationId);


    // ✅ 拿到刚插入的自增 id（MySQL）
    @Select("SELECT LAST_INSERT_ID()")
    Long lastInsertId();

    @Select("""
            SELECT COUNT(1)
            FROM reservation r
            WHERE r.room_id = #{roomId}
              AND CONCAT(r.status, '') IN ('ACTIVE', '2')
              AND r.start_time < #{end}
              AND r.end_time > #{start}
            """)
    int countConflictByRoomId(@Param("roomId") Long roomId,
                              @Param("start") Timestamp start,
                              @Param("end") Timestamp end);

    @Select("""
            <script>
            SELECT DISTINCT room_id
            FROM reservation
            WHERE CONCAT(status, '') IN ('ACTIVE', '2')
              AND start_time &lt; #{end}
              AND end_time &gt; #{start}
              AND room_id IN
              <foreach collection="roomIds" item="roomId" open="(" separator="," close=")">
                #{roomId}
              </foreach>
            </script>
            """)
    List<Long> selectConflictRoomIds(@Param("start") Timestamp start,
                                     @Param("end") Timestamp end,
                                     @Param("roomIds") List<Long> roomIds);

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
                CASE CONCAT(status, '')
                    WHEN '1' THEN 'PENDING'
                    WHEN '2' THEN 'ACTIVE'
                    WHEN '3' THEN 'ENDED'
                    WHEN '4' THEN 'CANCELLED'
                    WHEN '5' THEN 'REJECTED'
                    WHEN '6' THEN 'EXCEPTION'
                    ELSE CONCAT(status, '')
                END AS status,
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
                    CONCAT(r.start_time, '') AS startTime,
                    CONCAT(r.end_time, '') AS endTime,
                    CASE CONCAT(r.status, '')
                        WHEN '1' THEN 'PENDING'
                        WHEN '2' THEN 'ACTIVE'
                        WHEN '3' THEN 'ENDED'
                        WHEN '4' THEN 'CANCELLED'
                        WHEN '5' THEN 'REJECTED'
                        WHEN '6' THEN 'EXCEPTION'
                        ELSE CONCAT(r.status, '')
                    END AS status,
                    r.remark,
                    r.cancel_reason AS cancelReason,
                    CASE
                        WHEN r.organizer_id = #{currentUserId} THEN 'ORGANIZER'
                        ELSE 'PARTICIPANT'
                    END AS role,
                    CASE
                        WHEN r.organizer_id = #{currentUserId}
                             AND CONCAT(r.status, '') IN ('ACTIVE', '2')
                             AND r.end_time &gt; NOW()
                        THEN TRUE ELSE FALSE
                    END AS canEdit,
                    CASE
                        WHEN r.organizer_id = #{currentUserId}
                             AND CONCAT(r.status, '') IN ('ACTIVE', '2')
                             AND r.end_time &gt; NOW()
                        THEN TRUE ELSE FALSE
                    END AS canCancel
                FROM reservation r
                JOIN meeting_room m ON m.id = r.room_id
                JOIN sys_user u ON u.id = r.organizer_id
                WHERE r.start_time &gt;= #{start}
                  AND r.start_time &lt; #{end}
                  <if test="status != null and status != ''">
                    AND (
                      CONCAT(r.status, '') = #{status}
                      OR CONCAT(r.status, '') = CASE #{status}
                        WHEN 'PENDING' THEN '1'
                        WHEN 'ACTIVE' THEN '2'
                        WHEN 'ENDED' THEN '3'
                        WHEN 'CANCELLED' THEN '4'
                        WHEN 'REJECTED' THEN '5'
                        WHEN 'EXCEPTION' THEN '6'
                        ELSE #{status}
                      END
                    )
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
                SELECT COUNT(1)
                FROM reservation r
                WHERE CONCAT(r.status, '') IN ('ENDED', '3')
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
            </script>
            """)
    long countMyEndedReservations(@Param("currentUserId") Long currentUserId,
                                  @Param("scope") String scope);

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
                    CONCAT(r.start_time, '') AS startTime,
                    CONCAT(r.end_time, '') AS endTime,
                    CASE CONCAT(r.status, '')
                        WHEN '1' THEN 'PENDING'
                        WHEN '2' THEN 'ACTIVE'
                        WHEN '3' THEN 'ENDED'
                        WHEN '4' THEN 'CANCELLED'
                        WHEN '5' THEN 'REJECTED'
                        WHEN '6' THEN 'EXCEPTION'
                        ELSE CONCAT(r.status, '')
                    END AS status,
                    r.remark,
                    r.cancel_reason AS cancelReason,
                    CASE
                        WHEN r.organizer_id = #{currentUserId} THEN 'ORGANIZER'
                        ELSE 'PARTICIPANT'
                    END AS role,
                    FALSE AS canEdit,
                    FALSE AS canCancel
                FROM reservation r
                JOIN meeting_room m ON m.id = r.room_id
                JOIN sys_user u ON u.id = r.organizer_id
                WHERE CONCAT(r.status, '') IN ('ENDED', '3')
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
                ORDER BY r.end_time DESC, r.id DESC
                LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<MyReservationVO> selectMyEndedReservationsPage(@Param("currentUserId") Long currentUserId,
                                                        @Param("scope") String scope,
                                                        @Param("limit") int limit,
                                                        @Param("offset") int offset);

    @Select("""
            <script>
            SELECT
              reservation_id AS reservationId,
              user_id AS userId,
              rating,
              content,
              created_at AS createdAt
            FROM reservation_review
            WHERE user_id = #{currentUserId}
              AND reservation_id IN
              <foreach collection="reservationIds" item="reservationId" open="(" separator="," close=")">
                #{reservationId}
              </foreach>
            ORDER BY reservation_id ASC
            </script>
            """)
    List<ReservationReviewRow> selectMyReservationReviews(@Param("currentUserId") Long currentUserId,
                                                          @Param("reservationIds") List<Long> reservationIds);

    @Select("""
            <script>
            SELECT
              rd.reservation_id AS reservationId,
              rd.device_id AS id,
              rd.device_id AS deviceId,
              d.device_code AS deviceCode,
              d.name,
              rd.quantity,
              CASE CONCAT(d.status, '')
                WHEN '1' THEN 'ENABLED'
                WHEN '0' THEN 'DISABLED'
                ELSE CONCAT(d.status, '')
              END AS status
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
            <script>
            SELECT
              rp.reservation_id AS reservationId,
              u.id AS id,
              u.id AS userId,
              u.username AS username,
              u.display_name AS displayName
            FROM reservation_participant rp
            JOIN sys_user u ON u.id = rp.user_id
            WHERE rp.reservation_id IN
            <foreach collection="reservationIds" item="reservationId" open="(" separator="," close=")">
              #{reservationId}
            </foreach>
            ORDER BY rp.reservation_id ASC, u.id ASC
            </script>
            """)
    List<ReservationParticipantRow> selectReservationParticipants(@Param("reservationIds") List<Long> reservationIds);

    @Select("""
            SELECT
                id,
                organizer_id AS organizerId,
                room_id AS roomId,
                CASE CONCAT(status, '')
                    WHEN '1' THEN 'PENDING'
                    WHEN '2' THEN 'ACTIVE'
                    WHEN '3' THEN 'ENDED'
                    WHEN '4' THEN 'CANCELLED'
                    WHEN '5' THEN 'REJECTED'
                    WHEN '6' THEN 'EXCEPTION'
                    ELSE CONCAT(status, '')
                END AS status,
                end_time AS endTime
            FROM reservation
            WHERE id = #{id}
              AND organizer_id = #{organizerId}
            LIMIT 1
            """)
    ReservationEditableRow selectEditableReservation(@Param("id") Long id,
                                                     @Param("organizerId") Long organizerId);

    @Select("""
            SELECT
              r.id,
              CASE CONCAT(r.status, '')
                WHEN '1' THEN 'PENDING'
                WHEN '2' THEN 'ACTIVE'
                WHEN '3' THEN 'ENDED'
                WHEN '4' THEN 'CANCELLED'
                WHEN '5' THEN 'REJECTED'
                WHEN '6' THEN 'EXCEPTION'
                ELSE CONCAT(r.status, '')
              END AS status
            FROM reservation r
            WHERE r.id = #{id}
              AND (
                r.organizer_id = #{currentUserId}
                OR EXISTS (
                  SELECT 1
                  FROM reservation_participant rp
                  WHERE rp.reservation_id = r.id
                    AND rp.user_id = #{currentUserId}
                )
              )
            LIMIT 1
            """)
    ReviewableReservationRow selectReviewableReservation(@Param("id") Long id,
                                                         @Param("currentUserId") Long currentUserId);

    @Select("""
            SELECT
              reservation_id AS reservationId,
              user_id AS userId,
              rating,
              content,
              created_at AS createdAt
            FROM reservation_review
            WHERE reservation_id = #{reservationId}
              AND user_id = #{userId}
            LIMIT 1
            """)
    ReservationReviewRow selectReservationReviewByReservationIdAndUserId(@Param("reservationId") Long reservationId,
                                                                         @Param("userId") Long userId);

    @Insert("""
            INSERT INTO reservation_review(
              reservation_id,
              user_id,
              rating,
              content,
              created_at,
              updated_at
            )
            VALUES(
              #{reservationId},
              #{userId},
              #{rating},
              #{content},
              NOW(),
              NOW()
            )
            """)
    int insertReservationReview(@Param("reservationId") Long reservationId,
                                @Param("userId") Long userId,
                                @Param("rating") Integer rating,
                                @Param("content") String content);

    @Select("""
            SELECT COUNT(1)
            FROM reservation
            WHERE room_id = #{roomId}
              AND id <> #{reservationId}
              AND CONCAT(status, '') IN ('ACTIVE', '2')
              AND start_time < #{end}
              AND end_time > #{start}
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
            SET status = 4,
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
                CONCAT(r.start_time, '') AS startTime,
                CONCAT(r.end_time, '') AS endTime,
                CASE CONCAT(r.status, '')
                    WHEN '1' THEN 'PENDING'
                    WHEN '2' THEN 'ACTIVE'
                    WHEN '3' THEN 'ENDED'
                    WHEN '4' THEN 'CANCELLED'
                    WHEN '5' THEN 'REJECTED'
                    WHEN '6' THEN 'EXCEPTION'
                    ELSE CONCAT(r.status, '')
                END AS status,
                r.remark,
                r.cancel_reason AS cancelReason,
                'ORGANIZER' AS role,
                CASE
                    WHEN CONCAT(r.status, '') IN ('ACTIVE', '2') AND r.end_time > NOW()
                    THEN TRUE ELSE FALSE
                END AS canEdit,
                CASE
                    WHEN CONCAT(r.status, '') IN ('ACTIVE', '2') AND r.end_time > NOW()
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

    @Select("""
            SELECT
              r.id AS reservationId,
              r.organizer_id AS userId,
              r.title
            FROM reservation r
            WHERE CONCAT(r.status, '') IN ('ACTIVE', '2')
              AND r.end_time < #{now}
            UNION
            SELECT
              r.id AS reservationId,
              rp.user_id AS userId,
              r.title
            FROM reservation r
            JOIN reservation_participant rp ON rp.reservation_id = r.id
            WHERE CONCAT(r.status, '') IN ('ACTIVE', '2')
              AND r.end_time < #{now}
            """)
    List<ReviewTodoTargetRow> selectReviewTodoTargets(@Param("now") Timestamp now);

    @Update("""
                UPDATE reservation
                SET status = 3
                WHERE CONCAT(status, '') IN ('ACTIVE', '2')
                  AND end_time < #{now}
            """)
    int markEnded(@Param("now") Timestamp now);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM reservation r
            JOIN meeting_room m ON m.id = r.room_id
            JOIN sys_user u ON u.id = r.organizer_id
            WHERE 1 = 1
              <if test="keyword != null and keyword != ''">
                AND (
                  r.reservation_no LIKE CONCAT('%', #{keyword}, '%')
                  OR r.title LIKE CONCAT('%', #{keyword}, '%')
                  OR m.name LIKE CONCAT('%', #{keyword}, '%')
                  OR u.display_name LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test="status != null and status != ''">
                AND (
                  CONCAT(r.status, '') = #{status}
                  OR CONCAT(r.status, '') = CASE #{status}
                    WHEN 'PENDING' THEN '1'
                    WHEN 'ACTIVE' THEN '2'
                    WHEN 'ENDED' THEN '3'
                    WHEN 'CANCELLED' THEN '4'
                    WHEN 'REJECTED' THEN '5'
                    WHEN 'EXCEPTION' THEN '6'
                    ELSE #{status}
                  END
                )
              </if>
            </script>
            """)
    Long countAdminReservations(@Param("keyword") String keyword,
                                @Param("status") String status);

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
              r.organizer_id AS organizerId,
              u.display_name AS organizerName,
              r.title,
              r.attendees,
              DATE_FORMAT(r.start_time, '%Y-%m-%d %H:%i:%s') AS startTime,
              DATE_FORMAT(r.end_time, '%Y-%m-%d %H:%i:%s') AS endTime,
              CASE CONCAT(r.status, '')
                WHEN '1' THEN 'PENDING'
                WHEN '2' THEN 'ACTIVE'
                WHEN '3' THEN 'ENDED'
                WHEN '4' THEN 'CANCELLED'
                WHEN '5' THEN 'REJECTED'
                WHEN '6' THEN 'EXCEPTION'
                ELSE CONCAT(r.status, '')
              END AS status,
              r.remark,
              r.cancel_reason AS cancelReason,
              r.approval_remark AS approvalRemark,
              r.reject_reason AS rejectReason,
              r.exception_reason AS exceptionReason,
              pu.display_name AS processedByName,
              DATE_FORMAT(r.processed_at, '%Y-%m-%d %H:%i:%s') AS processedAt,
              DATE_FORMAT(r.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt
            FROM reservation r
            JOIN meeting_room m ON m.id = r.room_id
            JOIN sys_user u ON u.id = r.organizer_id
            LEFT JOIN sys_user pu ON pu.id = r.processed_by
            WHERE 1 = 1
              <if test="keyword != null and keyword != ''">
                AND (
                  r.reservation_no LIKE CONCAT('%', #{keyword}, '%')
                  OR r.title LIKE CONCAT('%', #{keyword}, '%')
                  OR m.name LIKE CONCAT('%', #{keyword}, '%')
                  OR u.display_name LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test="status != null and status != ''">
                AND (
                  CONCAT(r.status, '') = #{status}
                  OR CONCAT(r.status, '') = CASE #{status}
                    WHEN 'PENDING' THEN '1'
                    WHEN 'ACTIVE' THEN '2'
                    WHEN 'ENDED' THEN '3'
                    WHEN 'CANCELLED' THEN '4'
                    WHEN 'REJECTED' THEN '5'
                    WHEN 'EXCEPTION' THEN '6'
                    ELSE #{status}
                  END
                )
              </if>
            ORDER BY r.created_at DESC, r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminReservationItemVO> selectAdminReservations(@Param("keyword") String keyword,
                                                         @Param("status") String status,
                                                         @Param("limit") int limit,
                                                         @Param("offset") int offset);

    @Select("""
            SELECT
              r.id,
              r.reservation_no AS reservationNo,
              r.room_id AS roomId,
              m.room_code AS roomCode,
              m.name AS roomName,
              m.location AS roomLocation,
              m.capacity AS roomCapacity,
              r.organizer_id AS organizerId,
              u.display_name AS organizerName,
              r.title,
              r.attendees,
              DATE_FORMAT(r.start_time, '%Y-%m-%d %H:%i:%s') AS startTime,
              DATE_FORMAT(r.end_time, '%Y-%m-%d %H:%i:%s') AS endTime,
              CASE CONCAT(r.status, '')
                WHEN '1' THEN 'PENDING'
                WHEN '2' THEN 'ACTIVE'
                WHEN '3' THEN 'ENDED'
                WHEN '4' THEN 'CANCELLED'
                WHEN '5' THEN 'REJECTED'
                WHEN '6' THEN 'EXCEPTION'
                ELSE CONCAT(r.status, '')
              END AS status,
              r.remark,
              r.cancel_reason AS cancelReason,
              r.approval_remark AS approvalRemark,
              r.reject_reason AS rejectReason,
              r.exception_reason AS exceptionReason,
              pu.display_name AS processedByName,
              DATE_FORMAT(r.processed_at, '%Y-%m-%d %H:%i:%s') AS processedAt,
              DATE_FORMAT(r.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt
            FROM reservation r
            JOIN meeting_room m ON m.id = r.room_id
            JOIN sys_user u ON u.id = r.organizer_id
            LEFT JOIN sys_user pu ON pu.id = r.processed_by
            WHERE r.id = #{id}
            LIMIT 1
            """)
    AdminReservationItemVO selectAdminReservationById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM reservation r
            WHERE 1 = 1
              <if test="status != null and status != ''">
                AND (
                  CONCAT(r.status, '') = #{status}
                  OR CONCAT(r.status, '') = CASE #{status}
                    WHEN 'PENDING' THEN '1'
                    WHEN 'ACTIVE' THEN '2'
                    WHEN 'ENDED' THEN '3'
                    WHEN 'CANCELLED' THEN '4'
                    WHEN 'REJECTED' THEN '5'
                    WHEN 'EXCEPTION' THEN '6'
                    ELSE #{status}
                  END
                )
              </if>
            </script>
            """)
    Integer countAdminReservationsByStatus(@Param("status") String status);

    @Select("""
            SELECT
              r.id,
              r.room_id AS roomId,
              r.organizer_id AS organizerId,
              r.title,
              m.name AS roomName,
              r.attendees,
              CASE CONCAT(r.status, '')
                WHEN '1' THEN 'PENDING'
                WHEN '2' THEN 'ACTIVE'
                WHEN '3' THEN 'ENDED'
                WHEN '4' THEN 'CANCELLED'
                WHEN '5' THEN 'REJECTED'
                WHEN '6' THEN 'EXCEPTION'
                ELSE CONCAT(r.status, '')
              END AS status,
              r.start_time AS startTime,
              r.end_time AS endTime
            FROM reservation r
            JOIN meeting_room m ON m.id = r.room_id
            WHERE r.id = #{id}
            LIMIT 1
            """)
    AdminReservationProcessRow selectAdminReservationProcessById(@Param("id") Long id);

    @Update("""
            UPDATE reservation
            SET status = 2,
                approval_remark = #{approvalRemark},
                reject_reason = NULL,
                exception_reason = NULL,
                processed_by = #{adminUserId},
                processed_at = NOW(),
                updated_at = NOW()
            WHERE id = #{id}
              AND CONCAT(status, '') IN ('PENDING', '1')
            """)
    int approveReservation(@Param("id") Long id,
                           @Param("adminUserId") Long adminUserId,
                           @Param("approvalRemark") String approvalRemark);

    @Update("""
            UPDATE reservation
            SET status = 5,
                reject_reason = #{rejectReason},
                processed_by = #{adminUserId},
                processed_at = NOW(),
                updated_at = NOW()
            WHERE id = #{id}
              AND CONCAT(status, '') IN ('PENDING', '1')
            """)
    int rejectReservation(@Param("id") Long id,
                          @Param("adminUserId") Long adminUserId,
                          @Param("rejectReason") String rejectReason);

    @Update("""
            UPDATE reservation
            SET status = 6,
                exception_reason = #{exceptionReason},
                processed_by = #{adminUserId},
                processed_at = NOW(),
                updated_at = NOW()
            WHERE id = #{id}
              AND CONCAT(status, '') IN ('ACTIVE', '2')
            """)
    int markReservationException(@Param("id") Long id,
                                 @Param("adminUserId") Long adminUserId,
                                 @Param("exceptionReason") String exceptionReason);

    @Data
    class ReservationEditableRow {
        private Long id;
        private Long organizerId;
        private Long roomId;
        private String status;
        private Timestamp endTime;
    }

    @Data
    class AdminReservationProcessRow {
        private Long id;
        private Long roomId;
        private Long organizerId;
        private String title;
        private String roomName;
        private Integer attendees;
        private String status;
        private Timestamp startTime;
        private Timestamp endTime;
    }

    @Data
    class ReviewableReservationRow {
        private Long id;
        private String status;
    }

    @Data
    class ReservationReviewRow {
        private Long reservationId;
        private Long userId;
        private Integer rating;
        private String content;
        private Timestamp createdAt;
    }

    @Data
    class RoomRow {
        private Long id;
        private String roomCode;
        private String name;
        private String location;
        private Integer capacity;
        private String status;
        private String description;
    }

    @Data
    class ReviewTodoTargetRow {
        private Long reservationId;
        private Long userId;
        private String title;
    }

    @Data
    class ReservationParticipantRow {
        private Long reservationId;
        private Long id;
        private Long userId;
        private String username;
        private String displayName;
    }
}
