package com.llf.service.impl;

import com.llf.dto.AnalyticsQueryDTO;
import com.llf.mapper.AnalyticsMapper;
import com.llf.service.AnalyticsService;
import com.llf.vo.PageVO;
import com.llf.vo.analytics.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
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
    private static final String[] WEEKDAY_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    @Resource
    private AnalyticsMapper analyticsMapper;

    private Timestamp ts(Long ms) {
        return ms == null ? null : Timestamp.from(Instant.ofEpochMilli(ms));
    }

    private String dim(String d) {
        if (d == null || d.isBlank()) return "week";
        return d;
    }

    private int page(Integer p) { return (p == null || p < 1) ? 1 : p; }
    private int size(Integer s) { return (s == null || s < 1) ? 10 : Math.min(s, 50); }

    @Override
    public AnalyticsOverviewVO overview(AnalyticsQueryDTO q) {
        Timestamp start = ts(q.getStart());
        Timestamp end = ts(q.getEnd());
        String roomCode = q.getRoomCode();
        String status = q.getStatus();
        String dimension = dim(q.getDimension());

        long total = analyticsMapper.countTotal(start, end, roomCode, status);
        long cancelled = analyticsMapper.countTotal(start, end, roomCode, "CANCELLED");
        long activeMinutes = analyticsMapper.sumActiveMinutes(start, end, roomCode);
        long conflicts = analyticsMapper.countConflicts(start, end, roomCode);

        // 使用率：sum(activeMinutes) / (rangeMinutes * roomCount) * 100
        double utilization = 0;
        if (start != null && end != null && end.getTime() > start.getTime()) {
            long rangeMinutes = (end.getTime() - start.getTime()) / 60000;
            long roomCount = (roomCode == null || roomCode.isBlank()) ? analyticsMapper.countRooms() : 1;
            if (rangeMinutes > 0 && roomCount > 0) {
                utilization = (activeMinutes * 1.0) / (rangeMinutes * roomCount) * 100.0;
            }
        }

        double cancelRate = total == 0 ? 0 : (cancelled * 100.0 / total);

        AnalyticsKpiVO kpi = new AnalyticsKpiVO();
        kpi.setTotal(total);
        kpi.setUtilization(round2(utilization));
        kpi.setConflicts(conflicts);
        kpi.setCancelRate(round2(cancelRate));

        List<TrendPointVO> trend = analyticsMapper.trend(start, end, roomCode, status, dimension);
        List<StatusCountVO> dist = analyticsMapper.statusDist(start, end, roomCode);
        List<HeatmapCellVO> heatmap = analyticsMapper.heatmap(start, end, roomCode);
        List<RoomRankingVO> rankings = analyticsMapper.rankings(start, end, roomCode, status);

        int p = page(q.getPage());
        int s = size(q.getSize());
        int offset = (p - 1) * s;
        List<ReservationDetailVO> records = analyticsMapper.details(start, end, roomCode, status, s, offset);
        long dtTotal = analyticsMapper.detailsTotal(start, end, roomCode, status);

        PageVO<ReservationDetailVO> pageVO = new PageVO<>();
        pageVO.setPage(p);
        pageVO.setSize(s);
        pageVO.setTotal(dtTotal);
        pageVO.setRecords(records);

        AnalyticsOverviewVO vo = new AnalyticsOverviewVO();
        vo.setKpi(kpi);
        vo.setTrend(trend);
        vo.setStatusDist(dist);
        vo.setHeatmap(heatmap);
        vo.setRankings(rankings);
        vo.setDetails(pageVO);
        return vo;
    }

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

    @Override
    public String exportCsv(AnalyticsQueryDTO q) {
        AnalyticsOverviewVO ov = overview(q);
        StringBuilder sb = new StringBuilder();
        sb.append("id,title,roomCode,roomName,startTime,endTime,status\n");
        for (ReservationDetailVO r : ov.getDetails().getRecords()) {
            sb.append(r.getId()).append(",");
            sb.append(escape(r.getTitle())).append(",");
            sb.append(escape(r.getRoomCode())).append(",");
            sb.append(escape(r.getRoomName())).append(",");
            sb.append(r.getStartTime()).append(",");
            sb.append(r.getEndTime()).append(",");
            sb.append(escape(r.getStatus())).append("\n");
        }
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
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
        kpis.setTotalReservations(kpi("totalReservations", "近期开会预约", totalReservations, "次", "近窗口内预约总数", "primary"));
        kpis.setActiveReservations(kpi("activeReservations", "进行中预约", activeReservations, "次", "当前状态仍为 ACTIVE", "success"));
        kpis.setCancelledReservations(kpi("cancelledReservations", "已取消预约", cancelledReservations, "次", "近窗口内取消数量", "warning"));
        kpis.setRoomCoverageRate(kpi("roomCoverageRate", "会议室设备覆盖率", roomCoverageRate, "%", "已绑定静态设备的会议室占比", "primary"));
        kpis.setDeviceCoverageRate(kpi("deviceCoverageRate", "设备覆盖率", deviceCoverageRate, "%", "至少绑定到一个会议室的设备占比", "primary"));
        kpis.setMaintenanceRooms(kpi("maintenanceRooms", "维护中会议室", maintenanceRooms, "间", "当前状态为 MAINTENANCE", "warning"));
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
            alerts.add(alert(
                    "room_maintenance_" + row.roomId,
                    "room_maintenance",
                    row.roomName,
                    row.roomName + " 当前处于维护状态",
                    "warning"
            ));
        }
        for (AnalyticsMapper.UnboundRoomAlertRow row : analyticsMapper.selectUnboundRoomAlerts()) {
            alerts.add(alert(
                    "room_unbound_" + row.roomId,
                    "room_unbound",
                    row.roomName,
                    row.roomName + " 尚未绑定任何静态设备",
                    "warning"
            ));
        }
        for (AnalyticsMapper.DisabledBoundDeviceAlertRow row : analyticsMapper.selectDisabledBoundDeviceAlerts()) {
            alerts.add(alert(
                    "device_disabled_bound_" + row.deviceId,
                    "device_disabled_bound",
                    row.deviceName,
                    row.deviceName + " 已停用但仍绑定在会议室中",
                    "danger"
            ));
        }
        for (AnalyticsMapper.HighCancelRoomAlertRow row : analyticsMapper.selectHighCancelRoomAlerts(start, end)) {
            alerts.add(alert(
                    "room_high_cancel_" + row.roomId,
                    "room_high_cancel",
                    row.roomName,
                    row.roomName + " 近期开会取消率偏高（" + defaultZeroLong(row.cancelledCount) + "/" + defaultZeroLong(row.reservationCount) + "）",
                    "danger"
            ));
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
            item.setDeviceSummary(row.deviceSummary == null || row.deviceSummary.isBlank() ? "未调用设备" : row.deviceSummary);
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
