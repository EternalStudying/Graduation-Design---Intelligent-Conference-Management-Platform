package com.llf.service.impl;

import com.llf.mapper.ReservationMapper;
import com.llf.service.RoomRecommendService;
import com.llf.service.RoomService;
import com.llf.vo.RoomListItemVO;
import com.llf.vo.RoomRecommendItemVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoomRecommendServiceImpl implements RoomRecommendService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Resource
    private RoomService roomService;

    @Resource
    private ReservationMapper reservationMapper;

    @Override
    public List<RoomRecommendItemVO> recommend(String startIso, String endIso, Integer attendees, String requiredDevicesCsv) {
        if (startIso == null || endIso == null || attendees == null) return List.of();

        LocalDateTime s = parseAnyToLocal(startIso);
        LocalDateTime e = parseAnyToLocal(endIso);
        if (!e.isAfter(s)) return List.of();

        Timestamp start = Timestamp.valueOf(s);
        Timestamp end = Timestamp.valueOf(e);

        Set<String> required = parseCsv(requiredDevicesCsv);

        // 候选：只过滤“维护/容量不足/冲突”，设备缺失不直接过滤，只降分+提示
        List<RoomListItemVO> rooms = roomService.listRooms(null, null, null);

        List<RoomRecommendItemVO> out = new ArrayList<>();
        for (RoomListItemVO r : rooms) {
            if (!"AVAILABLE".equalsIgnoreCase(r.getStatus())) continue;
            if (r.getCapacity() == null || r.getCapacity() < attendees) continue;

            int conflict = reservationMapper.countConflictByRoomId(r.getId(), start, end);
            if (conflict > 0) continue;

            List<String> devs = Optional.ofNullable(r.getDevices()).orElse(List.of());
            List<String> missing = required.stream().filter(x -> !devs.contains(x)).toList();

            int wastePct = calcWastePct(r.getCapacity(), attendees);
            int score = score(wastePct, missing.size());

            RoomRecommendItemVO item = new RoomRecommendItemVO();
            item.setRoom(r);
            item.setMissingDevices(missing);
            item.setWastePct(wastePct);
            item.setScore(score);
            out.add(item);
        }

        out.sort(Comparator
                .comparing(RoomRecommendItemVO::getScore).reversed()
                .thenComparing(x -> x.getRoom().getCapacity() == null ? Integer.MAX_VALUE : x.getRoom().getCapacity()));
        return out;
    }

    private Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int calcWastePct(int capacity, int attendees) {
        if (capacity <= 0) return 100;
        int waste = Math.max(0, capacity - attendees);
        return (int) Math.round((waste * 100.0) / capacity);
    }

    private int score(int wastePct, int missingCount) {
        // 100 起步：浪费越少越高，缺设备扣分更明显
        int s = 100;
        s -= Math.min(60, (int) Math.round(wastePct * 0.8));
        s -= missingCount * 12;
        return Math.max(0, Math.min(100, s));
    }

    private static LocalDateTime parseAnyToLocal(String text) {
        // 1) 带偏移：2026-03-11T09:00:00+08:00
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (Exception ignore) {}

        // 2) UTC：2026-03-11T01:00:00Z
        try {
            return Instant.parse(text).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception ignore) {}

        // 3) 不带偏移：2026-03-11T09:00:00
        return LocalDateTime.parse(text);
    }
}

