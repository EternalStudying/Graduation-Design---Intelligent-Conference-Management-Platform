package com.llf.service.impl;

import com.llf.mapper.AnalyticsMapper;
import com.llf.service.AnalyticsService;
import com.llf.vo.admin.stats.AdminStatsVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final String[] WEEKDAY_LABELS = {"鍛ㄤ竴", "鍛ㄤ簩", "鍛ㄤ笁", "鍛ㄥ洓", "鍛ㄤ簲", "鍛ㄥ叚", "鍛ㄦ棩"};

    @Resource
    private AnalyticsMapper analyticsMapper;

    @Override
    public AdminStatsVO adminStats(Integer days) {
        int recentDays = normalizeDays(days);
        LocalDate today = LocalDate.now();
        LocalDateTime startDateTime = today.minusDays(recentDays - 1L).atStartOfDay();
        LocalDateTime endDateTime = today.plusDays(1L).atStartOfDay();
        Timestamp start = Timestamp.valueOf(startDateTime);
        Timestamp end = Timestamp.valueOf(endDateTime);

        int totalRooms = (int) analyticsMapper.countRooms();
        int configuredRoomCount = defaultZero(analyticsMapper.countConfiguredRooms());
        int unconfiguredRoomCount = Math.max(totalRooms - configuredRoomCount, 0);
        int totalDevices = defaultZero(analyticsMapper.countAllDevices());
        int enabledDevices = defaultZero(analyticsMapper.countEnabledDevices());
        int disabledDevices = defaultZero(analyticsMapper.countDisabledDevices());
        int maintenanceRooms = defaultZero(analyticsMapper.countMaintenanceRooms());
        int boundDeviceTypeCount = defaultZero(analyticsMapper.countBoundDeviceTypes());

        long totalReservations = analyticsMapper.countRecentReservations(start, end);
        long activeReservations = analyticsMapper.countRecentReservationsByStatus(start, end, "ACTIVE");
        long cancelledReservations = analyticsMapper.countRecentReservationsByStatus(start, end, "CANCELLED");

        AdminStatsVO stats = new AdminStatsVO();
        stats.setRecentDays(recentDays);
        stats.setKpis(buildKpis(
                totalReservations,
                activeReservations,
                cancelledReservations,
                coverageRate(configuredRoomCount, totalRooms),
                coverageRate(boundDeviceTypeCount, totalDevices),
                maintenanceRooms
        ));
        stats.setTrend(buildTrend(today, recentDays, analyticsMapper.selectRecentTrend(start, end)));
        stats.setHeatmap(buildHeatmap(analyticsMapper.selectRecentHeatmap(start, end)));
        stats.setRoomUsageRanks(buildRoomUsageRanks(analyticsMapper.selectRoomUsageRanks(start, end)));
        stats.setDeviceUsageRanks(buildDeviceUsageRanks(analyticsMapper.selectDeviceUsageRanks(start, end)));
        stats.setCoverage(buildCoverage(configuredRoomCount, unconfiguredRoomCount));
        stats.setAlerts(buildAlerts(start, end));
        stats.setReservations(buildRecentReservations(analyticsMapper.selectRecentReservations(start, end)));
        stats.setStaticSummary(buildStaticSummary(totalRooms, maintenanceRooms, totalDevices, enabledDevices, disabledDevices));
        return stats;
    }

    private int normalizeDays(Integer days) {
        if (days == null) {
            return 30;
        }
        return switch (days) {
            case 1, 7, 30 -> days;
            default -> 30;
        };
    }

    private AdminStatsVO.KpisVO buildKpis(long totalReservations,
                                          long activeReservations,
                                          long cancelledReservations,
                                          long roomCoverageRate,
                                          long deviceCoverageRate,
                                          int maintenanceRooms) {
        AdminStatsVO.KpisVO kpis = new AdminStatsVO.KpisVO();
        kpis.setTotalReservations(kpi("totalReservations", "杩戞湡寮€浼氶绾?", totalReservations, "娆?", "杩戠獥鍙ｅ唴棰勭害鎬绘暟", "primary"));
        kpis.setActiveReservations(kpi("activeReservations", "杩涜涓绾?", activeReservations, "娆?", "褰撳墠鐘舵€佷粛涓?ACTIVE", "success"));
        kpis.setCancelledReservations(kpi("cancelledReservations", "宸插彇娑堥绾?", cancelledReservations, "娆?", "杩戠獥鍙ｅ唴鍙栨秷鏁伴噺", "warning"));
        kpis.setRoomCoverageRate(kpi("roomCoverageRate", "浼氳瀹よ澶囪鐩栫巼", roomCoverageRate, "%", "宸茬粦瀹氶潤鎬佽澶囩殑浼氳瀹ゅ崰姣?", "primary"));
        kpis.setDeviceCoverageRate(kpi("deviceCoverageRate", "璁惧瑕嗙洊鐜?", deviceCoverageRate, "%", "鑷冲皯缁戝畾鍒颁竴涓細璁鐨勮澶囧崰姣?", "primary"));
        kpis.setMaintenanceRooms(kpi("maintenanceRooms", "缁存姢涓細璁", maintenanceRooms, "闂?", "褰撳墠鐘舵€佷负 MAINTENANCE", "warning"));
        return kpis;
    }

    private AdminStatsVO.KpiCardVO kpi(String key, String label, long value, String unit, String detail, String tone) {
        AdminStatsVO.KpiCardVO card = new AdminStatsVO.KpiCardVO();
        card.setKey(key);
        card.setLabel(label);
        card.setValue(value);
        card.setUnit(unit);
        card.setDetail(detail);
        card.setTone(tone);
        return card;
    }

    private List<AdminStatsVO.TrendItemVO> buildTrend(LocalDate today,
                                                      int recentDays,
                                                      List<AnalyticsMapper.RecentTrendRow> rows) {
        Map<String, Long> countMap = new LinkedHashMap<>();
        if (rows != null) {
            for (AnalyticsMapper.RecentTrendRow row : rows) {
                countMap.put(row.date, defaultZeroLong(row.reservationCount));
            }
        }

        List<AdminStatsVO.TrendItemVO> result = new ArrayList<>();
        for (int i = recentDays - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateText = date.format(DATE_FORMATTER);
            AdminStatsVO.TrendItemVO item = new AdminStatsVO.TrendItemVO();
            item.setDate(dateText);
            item.setLabel(date.format(LABEL_FORMATTER));
            item.setReservationCount(countMap.getOrDefault(dateText, 0L));
            result.add(item);
        }
        return result;
    }

    private List<AdminStatsVO.HeatmapItemVO> buildHeatmap(List<AnalyticsMapper.HeatmapStatRow> rows) {
        Map<String, Long> countMap = new LinkedHashMap<>();
        if (rows != null) {
            for (AnalyticsMapper.HeatmapStatRow row : rows) {
                countMap.put(heatmapKey(row.weekdayIndex, row.hourIndex), defaultZeroLong(row.reservationCount));
            }
        }

        List<AdminStatsVO.HeatmapItemVO> result = new ArrayList<>();
        for (int weekdayIndex = 0; weekdayIndex < WEEKDAY_LABELS.length; weekdayIndex++) {
            for (int hourIndex = 0; hourIndex < 14; hourIndex++) {
                AdminStatsVO.HeatmapItemVO item = new AdminStatsVO.HeatmapItemVO();
                item.setWeekday(WEEKDAY_LABELS[weekdayIndex]);
                item.setWeekdayIndex(weekdayIndex);
                item.setHour(String.format("%02d:00", hourIndex + 8));
                item.setHourIndex(hourIndex);
                item.setReservationCount(countMap.getOrDefault(heatmapKey(weekdayIndex, hourIndex), 0L));
                result.add(item);
            }
        }
        return result;
    }

    private List<AdminStatsVO.RoomUsageRankVO> buildRoomUsageRanks(List<AnalyticsMapper.RoomUsageRankRow> rows) {
        List<AdminStatsVO.RoomUsageRankVO> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (AnalyticsMapper.RoomUsageRankRow row : rows) {
            AdminStatsVO.RoomUsageRankVO item = new AdminStatsVO.RoomUsageRankVO();
            item.setRoomId(row.roomId);
            item.setRoomCode(row.roomCode);
            item.setRoomName(row.roomName);
            item.setLocation(row.location);
            item.setReservationCount(defaultZeroLong(row.reservationCount));
            item.setActiveCount(defaultZeroLong(row.activeCount));
            item.setCancelledCount(defaultZeroLong(row.cancelledCount));
            result.add(item);
        }
        return result;
    }

    private List<AdminStatsVO.DeviceUsageRankVO> buildDeviceUsageRanks(List<AnalyticsMapper.DeviceUsageRankRow> rows) {
        List<AdminStatsVO.DeviceUsageRankVO> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (AnalyticsMapper.DeviceUsageRankRow row : rows) {
            AdminStatsVO.DeviceUsageRankVO item = new AdminStatsVO.DeviceUsageRankVO();
            item.setDeviceId(row.deviceId);
            item.setDeviceCode(row.deviceCode);
            item.setDeviceName(row.deviceName);
            item.setStatus(row.status);
            item.setUsageQuantity(defaultZeroLong(row.usageQuantity));
            item.setReservationCount(defaultZeroLong(row.reservationCount));
            result.add(item);
        }
        return result;
    }

    private AdminStatsVO.CoverageVO buildCoverage(int configuredRoomCount, int unconfiguredRoomCount) {
        AdminStatsVO.CoverageVO coverage = new AdminStatsVO.CoverageVO();
        coverage.setConfiguredRoomCount(configuredRoomCount);
        coverage.setUnconfiguredRoomCount(unconfiguredRoomCount);
        return coverage;
    }

    private List<AdminStatsVO.AlertVO> buildAlerts(Timestamp start, Timestamp end) {
        List<AdminStatsVO.AlertVO> alerts = new ArrayList<>();

        for (AnalyticsMapper.MaintenanceRoomAlertRow row : analyticsMapper.selectMaintenanceRoomAlerts()) {
            alerts.add(alert("room_maintenance_" + row.roomId, "room_maintenance", row.roomName, row.roomName + " 褰撳墠澶勪簬缁存姢鐘舵€?", "warning"));
        }
        for (AnalyticsMapper.UnboundRoomAlertRow row : analyticsMapper.selectUnboundRoomAlerts()) {
            alerts.add(alert("room_unbound_" + row.roomId, "room_unbound", row.roomName, row.roomName + " 灏氭湭缁戝畾浠讳綍闈欐€佽澶?", "warning"));
        }
        for (AnalyticsMapper.DisabledBoundDeviceAlertRow row : analyticsMapper.selectDisabledBoundDeviceAlerts()) {
            alerts.add(alert("device_disabled_bound_" + row.deviceId, "device_disabled_bound", row.deviceName, row.deviceName + " 宸插仠鐢ㄤ絾浠嶇粦瀹氬湪浼氳瀹や腑", "danger"));
        }
        for (AnalyticsMapper.HighCancelRoomAlertRow row : analyticsMapper.selectHighCancelRoomAlerts(start, end)) {
            String summary = row.roomName + " 杩戞湡寮€浼氬彇娑堢巼鍋忛珮锛?" + defaultZeroLong(row.cancelledCount) + "/" + defaultZeroLong(row.reservationCount) + "锛?";
            alerts.add(alert("room_high_cancel_" + row.roomId, "room_high_cancel", row.roomName, summary, "danger"));
        }
        return alerts;
    }

    private AdminStatsVO.AlertVO alert(String id, String type, String targetName, String summary, String level) {
        AdminStatsVO.AlertVO alert = new AdminStatsVO.AlertVO();
        alert.setId(id);
        alert.setType(type);
        alert.setTargetName(targetName);
        alert.setSummary(summary);
        alert.setLevel(level);
        return alert;
    }

    private List<AdminStatsVO.ReservationItemVO> buildRecentReservations(List<AnalyticsMapper.RecentReservationRow> rows) {
        List<AdminStatsVO.ReservationItemVO> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (AnalyticsMapper.RecentReservationRow row : rows) {
            AdminStatsVO.ReservationItemVO item = new AdminStatsVO.ReservationItemVO();
            item.setId(row.id);
            item.setReservationNo(row.reservationNo);
            item.setTitle(row.title);
            item.setRoomName(row.roomName);
            item.setOrganizerName(row.organizerName);
            item.setStartTime(row.startTime);
            item.setEndTime(row.endTime);
            item.setStatus(row.status);
            item.setDeviceSummary(row.deviceSummary == null || row.deviceSummary.isBlank() ? "鏈皟鐢ㄨ澶?" : row.deviceSummary);
            result.add(item);
        }
        return result;
    }

    private AdminStatsVO.StaticSummaryVO buildStaticSummary(int totalRooms,
                                                           int maintenanceRooms,
                                                           int totalDevices,
                                                           int enabledDevices,
                                                           int disabledDevices) {
        AdminStatsVO.StaticSummaryVO summary = new AdminStatsVO.StaticSummaryVO();
        summary.setTotalRooms(totalRooms);
        summary.setAvailableRooms(Math.max(totalRooms - maintenanceRooms, 0));
        summary.setMaintenanceRooms(maintenanceRooms);
        summary.setTotalDevices(totalDevices);
        summary.setEnabledDevices(enabledDevices);
        summary.setDisabledDevices(disabledDevices);
        return summary;
    }

    private long coverageRate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0L;
        }
        return Math.round(numerator * 100.0 / denominator);
    }

    private String heatmapKey(Integer weekdayIndex, Integer hourIndex) {
        return weekdayIndex + "_" + hourIndex;
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private long defaultZeroLong(Long value) {
        return value == null ? 0L : value;
    }
}
