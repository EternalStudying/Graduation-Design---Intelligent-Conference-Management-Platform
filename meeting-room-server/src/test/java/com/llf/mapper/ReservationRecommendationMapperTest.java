package com.llf.mapper;

import com.llf.vo.MyReservationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MybatisTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting_room_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class ReservationRecommendationMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private ReservationMapper reservationMapper;

    @BeforeEach
    void setUpSchema() {
        execute(
                "DROP TABLE IF EXISTS reservation",
                "DROP TABLE IF EXISTS reservation_review",
                "DROP TABLE IF EXISTS reservation_participant",
                "DROP TABLE IF EXISTS sys_user",
                "DROP TABLE IF EXISTS room_device",
                "DROP TABLE IF EXISTS device",
                "DROP TABLE IF EXISTS meeting_room",
                """
                CREATE TABLE meeting_room (
                    id BIGINT PRIMARY KEY,
                    room_code VARCHAR(32) NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    location VARCHAR(128) NOT NULL,
                    capacity INT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    description VARCHAR(255)
                )
                """,
                """
                CREATE TABLE device (
                    id BIGINT PRIMARY KEY,
                    device_code VARCHAR(32) NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    total INT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    description VARCHAR(255)
                )
                """,
                """
                CREATE TABLE room_device (
                    room_id BIGINT NOT NULL,
                    device_id BIGINT NOT NULL,
                    quantity INT NOT NULL
                )
                """,
                """
                CREATE TABLE reservation (
                    id BIGINT PRIMARY KEY,
                    reservation_no VARCHAR(40) NOT NULL,
                    room_id BIGINT NOT NULL,
                    organizer_id BIGINT NOT NULL,
                    title VARCHAR(128) NOT NULL,
                    remark VARCHAR(255),
                    attendees INT NOT NULL,
                    start_time TIMESTAMP NOT NULL,
                    end_time TIMESTAMP NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    cancel_reason VARCHAR(255)
                )
                """,
                """
                CREATE TABLE reservation_participant (
                    reservation_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    created_at TIMESTAMP NULL
                )
                """,
                """
                CREATE TABLE reservation_review (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    reservation_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    rating INT NOT NULL,
                    content VARCHAR(300),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    UNIQUE (reservation_id, user_id)
                )
                """,
                """
                CREATE TABLE sys_user (
                    id BIGINT PRIMARY KEY,
                    username VARCHAR(64),
                    display_name VARCHAR(64),
                    password_hash VARCHAR(255),
                    role VARCHAR(32),
                    status VARCHAR(32),
                    created_at TIMESTAMP NULL,
                    updated_at TIMESTAMP NULL
                )
                """
        );
    }

    @Test
    void selectRecommendationCandidates_shouldFilterByAvailableAndCapacity() {
        jdbcTemplate.update("INSERT INTO meeting_room(id, room_code, name, location, capacity, status, description) VALUES(1, 'R1', '一号', 'A楼', 8, 'AVAILABLE', NULL)");
        jdbcTemplate.update("INSERT INTO meeting_room(id, room_code, name, location, capacity, status, description) VALUES(2, 'R2', '二号', 'A楼', 6, 'AVAILABLE', NULL)");
        jdbcTemplate.update("INSERT INTO meeting_room(id, room_code, name, location, capacity, status, description) VALUES(3, 'R3', '三号', 'A楼', 10, 'MAINTENANCE', NULL)");
        jdbcTemplate.update("INSERT INTO meeting_room(id, room_code, name, location, capacity, status, description) VALUES(4, 'R4', '四号', 'A楼', 12, 'AVAILABLE', NULL)");

        List<RoomMapper.RecommendationRoomRow> rows = roomMapper.selectRecommendationCandidates(8);

        assertEquals(List.of(1L, 4L), rows.stream().map(RoomMapper.RecommendationRoomRow::getId).toList());
    }

    @Test
    void selectConflictRoomIds_shouldOnlyReturnActiveOverlaps() {
        insertReservation(1L, 1L, "ACTIVE", "2026-04-15T09:00:00", "2026-04-15T10:00:00");
        insertReservation(2L, 2L, "CANCELLED", "2026-04-15T09:00:00", "2026-04-15T10:00:00");
        insertReservation(3L, 3L, "ENDED", "2026-04-15T09:00:00", "2026-04-15T10:00:00");
        insertReservation(4L, 4L, "ACTIVE", "2026-04-15T10:30:00", "2026-04-15T11:30:00");

        List<Long> roomIds = reservationMapper.selectConflictRoomIds(
                Timestamp.valueOf(LocalDateTime.parse("2026-04-15T09:30:00")),
                Timestamp.valueOf(LocalDateTime.parse("2026-04-15T10:30:00")),
                List.of(1L, 2L, 3L, 4L)
        );

        assertEquals(List.of(1L), roomIds);
    }

    @Test
    void selectEnabledRoomDevices_shouldExcludeDisabledDevices() {
        jdbcTemplate.update("INSERT INTO device(id, device_code, name, total, status, description) VALUES(1, 'D1', '投影仪', 5, 'ENABLED', NULL)");
        jdbcTemplate.update("INSERT INTO device(id, device_code, name, total, status, description) VALUES(2, 'D2', '白板', 5, 'DISABLED', NULL)");
        jdbcTemplate.update("INSERT INTO room_device(room_id, device_id, quantity) VALUES(10, 1, 2)");
        jdbcTemplate.update("INSERT INTO room_device(room_id, device_id, quantity) VALUES(10, 2, 1)");

        List<RoomMapper.RoomDeviceAvailabilityRow> rows = roomMapper.selectEnabledRoomDevices(List.of(10L));

        assertEquals(1, rows.size());
        assertEquals(1L, rows.get(0).getDeviceId());
        assertEquals(2, rows.get(0).getQuantity());
    }

    @Test
    void countConflictExcludeSelf_shouldIgnoreSelfAndReturnOtherActiveOverlaps() {
        insertReservation(70L, 3L, "ACTIVE", "2026-04-17T10:30:00", "2026-04-17T12:30:00");
        insertReservation(71L, 3L, "ACTIVE", "2026-04-17T11:00:00", "2026-04-17T12:00:00");
        insertReservation(72L, 3L, "CANCELLED", "2026-04-17T11:00:00", "2026-04-17T12:00:00");

        int count = reservationMapper.countConflictExcludeSelf(
                70L,
                3L,
                Timestamp.valueOf(LocalDateTime.parse("2026-04-17T10:30:00")),
                Timestamp.valueOf(LocalDateTime.parse("2026-04-17T12:30:00"))
        );

        assertEquals(1, count);
    }

    @Test
    void selectMyReservationReviews_shouldReturnCurrentUsersReviewOnly() {
        jdbcTemplate.update(
                "INSERT INTO reservation_review(reservation_id, user_id, rating, content, created_at, updated_at) VALUES(?, ?, ?, ?, ?, ?)",
                90L, 1L, 5, "很满意",
                Timestamp.valueOf(LocalDateTime.parse("2026-04-15T16:30:00")),
                Timestamp.valueOf(LocalDateTime.parse("2026-04-15T16:30:00"))
        );
        jdbcTemplate.update(
                "INSERT INTO reservation_review(reservation_id, user_id, rating, content, created_at, updated_at) VALUES(?, ?, ?, ?, ?, ?)",
                90L, 2L, 3, "一般",
                Timestamp.valueOf(LocalDateTime.parse("2026-04-15T17:00:00")),
                Timestamp.valueOf(LocalDateTime.parse("2026-04-15T17:00:00"))
        );

        List<ReservationMapper.ReservationReviewRow> rows = reservationMapper.selectMyReservationReviews(1L, List.of(90L));

        assertEquals(1, rows.size());
        assertEquals(5, rows.get(0).getRating());
        assertEquals("很满意", rows.get(0).getContent());
    }

    @Test
    void selectReviewableReservation_shouldAllowOrganizerOrParticipant() {
        insertReservation(91L, 4L, "ENDED", "2026-04-18T09:00:00", "2026-04-18T10:00:00");
        jdbcTemplate.update("INSERT INTO reservation_participant(reservation_id, user_id, created_at) VALUES(?, ?, ?)",
                91L, 2L, Timestamp.valueOf(LocalDateTime.parse("2026-04-15T10:00:00")));

        ReservationMapper.ReviewableReservationRow organizerRow = reservationMapper.selectReviewableReservation(91L, 1L);
        ReservationMapper.ReviewableReservationRow participantRow = reservationMapper.selectReviewableReservation(91L, 2L);
        ReservationMapper.ReviewableReservationRow otherRow = reservationMapper.selectReviewableReservation(91L, 3L);

        assertEquals("ENDED", organizerRow.getStatus());
        assertEquals("ENDED", participantRow.getStatus());
        assertEquals(null, otherRow);
    }

    @Test
    void selectMyEndedReservationsPage_shouldReturnEndedReservationsByEndTimeDesc() {
        jdbcTemplate.update("INSERT INTO sys_user(id, username, display_name, password_hash, role, status) VALUES(1, 'u1', '张三', 'x', 'USER', 'ENABLED')");
        jdbcTemplate.update("INSERT INTO meeting_room(id, room_code, name, location, capacity, status, description) VALUES(4, 'R4', '四号', 'A楼', 12, 'AVAILABLE', NULL)");
        insertReservation(100L, 4L, "ENDED", "2026-04-18T09:00:00", "2026-04-18T10:00:00");
        insertReservation(101L, 4L, "ENDED", "2026-04-19T09:00:00", "2026-04-19T11:00:00");
        insertReservation(102L, 4L, "ACTIVE", "2026-04-20T09:00:00", "2026-04-20T10:00:00");

        long total = reservationMapper.countMyEndedReservations(1L, "all");
        List<MyReservationVO> rows = reservationMapper.selectMyEndedReservationsPage(1L, "all", 8, 0);

        assertEquals(2L, total);
        assertEquals(List.of(101L, 100L), rows.stream().map(MyReservationVO::getId).toList());
    }

    private void insertReservation(Long id, Long roomId, String status, String startTime, String endTime) {
        jdbcTemplate.update(
                "INSERT INTO reservation(id, reservation_no, room_id, organizer_id, title, remark, attendees, start_time, end_time, status, cancel_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                "RSV-" + id,
                roomId,
                1L,
                "会议" + id,
                null,
                6,
                Timestamp.valueOf(LocalDateTime.parse(startTime)),
                Timestamp.valueOf(LocalDateTime.parse(endTime)),
                status,
                null
        );
    }

    private void execute(String... sqlList) {
        for (String sql : sqlList) {
            jdbcTemplate.execute(sql);
        }
    }
}
