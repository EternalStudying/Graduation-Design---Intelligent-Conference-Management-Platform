package com.llf.mapper;

import com.llf.vo.room.RoomDeviceOptionVO;
import com.llf.vo.room.RoomPageItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MybatisTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:room_mapper_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class RoomMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoomMapper roomMapper;

    @BeforeEach
    void setUpSchema() {
        execute(
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
                    description VARCHAR(255),
                    maintenance_remark VARCHAR(255)
                )
                """,
                """
                CREATE TABLE device (
                    id BIGINT PRIMARY KEY,
                    device_code VARCHAR(32) NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    total INT NOT NULL,
                    status VARCHAR(32) NOT NULL
                )
                """,
                """
                CREATE TABLE room_device (
                    room_id BIGINT NOT NULL,
                    device_id BIGINT NOT NULL,
                    quantity INT NOT NULL
                )
                """
        );
    }

    @Test
    void selectRoomPage_shouldRequireAllSelectedEnabledDevices() {
        insertRoom(1L, "R1", "路演室");
        insertRoom(2L, "R2", "白板室");
        insertRoom(3L, "R3", "停用设备室");
        insertDevice(1L, "投影仪", "ENABLED");
        insertDevice(2L, "电子白板", "ENABLED");
        insertDevice(3L, "旧电视", "DISABLED");
        bindDevice(1L, 1L);
        bindDevice(1L, 2L);
        bindDevice(2L, 2L);
        bindDevice(3L, 1L);
        bindDevice(3L, 3L);

        Long total = roomMapper.countRoomsForPage(null, null, null, null, List.of(1L, 2L), 2);
        List<RoomPageItemVO> rooms = roomMapper.selectRoomPage(null, null, null, null, List.of(1L, 2L), 2, 0, 10);

        assertEquals(1L, total);
        assertEquals(List.of(1L), rooms.stream().map(RoomPageItemVO::getId).toList());
    }

    @Test
    void selectRoomDeviceOptions_shouldReturnEnabledBoundDevicesOnly() {
        insertRoom(1L, "R1", "路演室");
        insertDevice(1L, "投影仪", "ENABLED");
        insertDevice(2L, "旧电视", "DISABLED");
        insertDevice(3L, "未绑定音箱", "ENABLED");
        bindDevice(1L, 1L);
        bindDevice(1L, 2L);

        List<RoomDeviceOptionVO> options = roomMapper.selectRoomDeviceOptions();

        assertEquals(1, options.size());
        assertEquals(1L, options.get(0).getId());
        assertEquals("投影仪", options.get(0).getName());
    }

    private void insertRoom(Long id, String code, String name) {
        jdbcTemplate.update(
                "INSERT INTO meeting_room(id, room_code, name, location, capacity, status, description) VALUES(?, ?, ?, 'A楼', 12, 'AVAILABLE', NULL)",
                id, code, name
        );
    }

    private void insertDevice(Long id, String name, String status) {
        jdbcTemplate.update(
                "INSERT INTO device(id, device_code, name, total, status) VALUES(?, ?, ?, 5, ?)",
                id, "D" + id, name, status
        );
    }

    private void bindDevice(Long roomId, Long deviceId) {
        jdbcTemplate.update("INSERT INTO room_device(room_id, device_id, quantity) VALUES(?, ?, 1)", roomId, deviceId);
    }

    private void execute(String... sqlStatements) {
        for (String sql : sqlStatements) {
            jdbcTemplate.execute(sql);
        }
    }
}
