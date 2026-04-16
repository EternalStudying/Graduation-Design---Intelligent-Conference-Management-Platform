package com.llf.vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llf.vo.analytics.ReservationDetailVO;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JsonTimeZoneSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeDashboardScheduleTimeAsShanghaiTime() throws Exception {
        DashboardTodayScheduleVO vo = new DashboardTodayScheduleVO();
        vo.setId(1L);
        vo.setStartTime(Timestamp.from(Instant.parse("2026-04-16T01:00:00Z")));
        vo.setEndTime(Timestamp.from(Instant.parse("2026-04-16T02:30:00Z")));

        String json = objectMapper.writeValueAsString(vo);

        assertThat(json)
                .contains("\"startTime\":\"2026-04-16 09:00:00\"")
                .contains("\"endTime\":\"2026-04-16 10:30:00\"");
    }

    @Test
    void shouldSerializeAnalyticsReservationDetailTimeAsShanghaiTime() throws Exception {
        ReservationDetailVO vo = new ReservationDetailVO();
        vo.setId(1L);
        vo.setStartTime(Timestamp.from(Instant.parse("2026-04-18T07:00:00Z")));
        vo.setEndTime(Timestamp.from(Instant.parse("2026-04-18T08:00:00Z")));

        String json = objectMapper.writeValueAsString(vo);

        assertThat(json)
                .contains("\"startTime\":\"2026-04-18 15:00:00\"")
                .contains("\"endTime\":\"2026-04-18 16:00:00\"");
    }
}
