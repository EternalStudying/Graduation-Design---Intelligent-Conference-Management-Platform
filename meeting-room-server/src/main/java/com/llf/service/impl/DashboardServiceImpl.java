package com.llf.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.mapper.RoomMapper;
import com.llf.mapper.ReservationMapper;
import com.llf.result.BizException;
import com.llf.service.DashboardService;
import com.llf.vo.DashboardOverviewSummaryVO;
import com.llf.vo.DashboardOverviewVO;
import com.llf.vo.DashboardQuoteVO;
import com.llf.vo.DashboardReservationSlotVO;
import com.llf.vo.DashboardRoomStatusVO;
import com.llf.vo.DashboardSummaryVO;
import com.llf.vo.DashboardTodayScheduleVO;
import com.llf.vo.RoomListItemVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String QUOTE_API = "https://v1.hitokoto.cn/?c=i&encode=json";
    private static final String FALLBACK_QUOTE = "把自己活成一道光，因为你不知道，谁会借着你的光走出了黑暗。";
    private static final String FALLBACK_AUTHOR = "泰戈尔";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private RoomMapper roomMapper;
    @Resource
    private ReservationMapper reservationMapper;

    @Override
    public DashboardSummaryVO getSummary() {
        DashboardSummaryVO vo = new DashboardSummaryVO();
        vo.setRoomTotal(roomMapper.countAll());
        vo.setAvailableRoomCount(roomMapper.countAvailable());
        vo.setActiveReservationCount(reservationMapper.countActive());
        return vo;
    }

    @Override
    public DashboardOverviewVO getOverview() {
        AuthUser currentUser = AuthContext.get();
        if (currentUser == null) {
            throw new BizException(401, "not logged in");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        Timestamp nowTs = Timestamp.valueOf(now);
        Timestamp dayStartTs = Timestamp.valueOf(dayStart);
        Timestamp dayEndTs = Timestamp.valueOf(dayEnd);

        List<DashboardReservationSlotVO> todayReservations = defaultList(
                reservationMapper.selectTodayOverviewReservations(dayStartTs, dayEndTs)
        );
        List<RoomListItemVO> rooms = defaultList(roomMapper.selectRooms(null, null, null));
        rooms.sort(Comparator.comparing(RoomListItemVO::getId));

        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setPeakWindow(buildPeakWindow(todayReservations, dayStart, dayEnd));
        vo.setSummary(buildSummary(currentUser.getId(), todayReservations, rooms, now, nowTs, dayStart, dayEnd));
        vo.setTodaySchedules(defaultList(reservationMapper.selectTodaySchedulesByOrganizerId(currentUser.getId(), dayStartTs, dayEndTs)));
        vo.setRoomStatuses(buildRoomStatuses(todayReservations, rooms, now));
        vo.setTodoItems(List.of());
        return vo;
    }

    @Override
    public DashboardQuoteVO getQuote() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(QUOTE_API))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                String quote = textOrDefault(root.path("hitokoto"), FALLBACK_QUOTE);
                String author = firstNonBlank(
                        root.path("from_who").asText(null),
                        root.path("from").asText(null),
                        FALLBACK_AUTHOR
                );
                DashboardQuoteVO vo = new DashboardQuoteVO();
                vo.setQuote(quote);
                vo.setQuoteAuthor(author);
                return vo;
            }
        } catch (Exception ignore) {
        }

        DashboardQuoteVO vo = new DashboardQuoteVO();
        vo.setQuote(FALLBACK_QUOTE);
        vo.setQuoteAuthor(FALLBACK_AUTHOR);
        return vo;
    }

    private DashboardOverviewSummaryVO buildSummary(Long userId,
                                                    List<DashboardReservationSlotVO> todayReservations,
                                                    List<RoomListItemVO> rooms,
                                                    LocalDateTime now,
                                                    Timestamp nowTs,
                                                    LocalDateTime dayStart,
                                                    LocalDateTime dayEnd) {
        DashboardOverviewSummaryVO summary = new DashboardOverviewSummaryVO();
        summary.setTodayMeetingCount(todayReservations.size());
        summary.setPendingCount(defaultZero(reservationMapper.countPendingByOrganizerId(userId, nowTs)));
        summary.setTotalRoomCount(rooms.size());
        summary.setAvailableRoomCount(countAvailableRooms(rooms, buildBusyRoomMap(todayReservations, now).keySet()));
        summary.setUtilizationRate(calculateUtilizationRate(todayReservations, rooms.size(), dayStart, dayEnd));
        return summary;
    }

    private List<DashboardRoomStatusVO> buildRoomStatuses(List<DashboardReservationSlotVO> todayReservations,
                                                          List<RoomListItemVO> rooms,
                                                          LocalDateTime now) {
        Map<Long, DashboardReservationSlotVO> busyRoomMap = buildBusyRoomMap(todayReservations, now);
        List<DashboardRoomStatusVO> result = new ArrayList<>(rooms.size());
        for (RoomListItemVO room : rooms) {
            DashboardRoomStatusVO item = new DashboardRoomStatusVO();
            item.setRoomId(room.getId());
            item.setRoomName(room.getName());

            if ("MAINTENANCE".equalsIgnoreCase(room.getStatus())) {
                item.setStatus("MAINTENANCE");
                item.setDisplayStatus("维护中");
                item.setDetail("维护中");
            } else if (busyRoomMap.containsKey(room.getId())) {
                item.setStatus("BUSY");
                item.setDisplayStatus("使用中");
                item.setDetail("至 " + formatTime(busyRoomMap.get(room.getId()).getEndTime().toLocalDateTime(), null));
            } else {
                item.setStatus("FREE");
                item.setDisplayStatus("空闲");
                item.setDetail(buildFreeRoomDetail(room.getId()));
            }

            result.add(item);
        }
        return result;
    }

    private String buildPeakWindow(List<DashboardReservationSlotVO> todayReservations,
                                   LocalDateTime dayStart,
                                   LocalDateTime dayEnd) {
        Map<LocalDateTime, Integer> deltas = new HashMap<>();
        for (DashboardReservationSlotVO reservation : todayReservations) {
            LocalDateTime start = max(reservation.getStartTime().toLocalDateTime(), dayStart);
            LocalDateTime end = min(reservation.getEndTime().toLocalDateTime(), dayEnd);
            if (!start.isBefore(end)) {
                continue;
            }
            deltas.merge(start, 1, Integer::sum);
            deltas.merge(end, -1, Integer::sum);
        }
        if (deltas.isEmpty()) {
            return "今日暂无高峰";
        }

        List<LocalDateTime> timePoints = new ArrayList<>(deltas.keySet());
        timePoints.sort(LocalDateTime::compareTo);

        int activeCount = 0;
        int maxCount = 0;
        LocalDateTime bestStart = null;
        LocalDateTime bestEnd = null;
        for (int i = 0; i < timePoints.size(); i++) {
            LocalDateTime current = timePoints.get(i);
            activeCount += deltas.get(current);
            LocalDateTime next = (i + 1 < timePoints.size()) ? timePoints.get(i + 1) : dayEnd;
            if (!current.isBefore(next) || activeCount <= 0) {
                continue;
            }

            if (activeCount > maxCount) {
                maxCount = activeCount;
                bestStart = current;
                bestEnd = next;
            } else if (activeCount == maxCount && bestEnd != null && bestEnd.equals(current)) {
                bestEnd = next;
            }
        }

        if (bestStart == null || bestEnd == null) {
            return "今日暂无高峰";
        }
        return formatTime(bestStart, dayEnd) + "-" + formatTime(bestEnd, dayEnd);
    }

    private int calculateUtilizationRate(List<DashboardReservationSlotVO> todayReservations,
                                         int totalRoomCount,
                                         LocalDateTime dayStart,
                                         LocalDateTime dayEnd) {
        if (totalRoomCount <= 0) {
            return 0;
        }

        long occupiedMinutes = 0L;
        for (DashboardReservationSlotVO reservation : todayReservations) {
            LocalDateTime start = max(reservation.getStartTime().toLocalDateTime(), dayStart);
            LocalDateTime end = min(reservation.getEndTime().toLocalDateTime(), dayEnd);
            if (start.isBefore(end)) {
                occupiedMinutes += java.time.Duration.between(start, end).toMinutes();
            }
        }

        long totalMinutes = totalRoomCount * 24L * 60L;
        return (int) Math.round(occupiedMinutes * 100.0D / totalMinutes);
    }

    private Map<Long, DashboardReservationSlotVO> buildBusyRoomMap(List<DashboardReservationSlotVO> todayReservations,
                                                                   LocalDateTime now) {
        Map<Long, DashboardReservationSlotVO> busyRoomMap = new HashMap<>();
        for (DashboardReservationSlotVO reservation : todayReservations) {
            if (!"ACTIVE".equalsIgnoreCase(reservation.getStatus())) {
                continue;
            }
            LocalDateTime start = reservation.getStartTime().toLocalDateTime();
            LocalDateTime end = reservation.getEndTime().toLocalDateTime();
            if (start.isAfter(now) || !end.isAfter(now)) {
                continue;
            }

            DashboardReservationSlotVO existing = busyRoomMap.get(reservation.getRoomId());
            if (existing == null || end.isBefore(existing.getEndTime().toLocalDateTime())) {
                busyRoomMap.put(reservation.getRoomId(), reservation);
            }
        }
        return busyRoomMap;
    }

    private int countAvailableRooms(List<RoomListItemVO> rooms, java.util.Set<Long> busyRoomIds) {
        int count = 0;
        for (RoomListItemVO room : rooms) {
            if ("AVAILABLE".equalsIgnoreCase(room.getStatus()) && !busyRoomIds.contains(room.getId())) {
                count++;
            }
        }
        return count;
    }

    private String buildFreeRoomDetail(Long roomId) {
        List<String> devices = defaultList(roomMapper.selectDeviceNamesByRoomId(roomId));
        if (devices.isEmpty()) {
            return "空闲可预订";
        }
        return "设备齐全";
    }

    private <T> List<T> defaultList(List<T> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        return left.isAfter(right) ? left : right;
    }

    private LocalDateTime min(LocalDateTime left, LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }

    private String formatTime(LocalDateTime value, LocalDateTime dayEnd) {
        if (dayEnd != null && value.equals(dayEnd)) {
            return "24:00";
        }
        return value.toLocalTime().format(TIME_FORMATTER);
    }

    private String textOrDefault(JsonNode node, String defaultValue) {
        if (node == null) {
            return defaultValue;
        }
        String value = node.asText(null);
        return firstNonBlank(value, defaultValue);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
