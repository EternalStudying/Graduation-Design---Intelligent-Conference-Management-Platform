package com.llf.assistant.semantic;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AiAssistantIntentFields {
    private Long reservationId;
    private Long roomId;
    private String roomName;
    private String title;
    private String meetingDate;
    private String dateFrom;
    private String dateTo;
    private String startClock;
    private String endClock;
    private Integer attendees;
    private Integer rating;
    private String content;
    private String deviceRequirements;
    private String targetScope;
    private String timeRangeLabel;
    private String relativeTarget;
    private String mutationHint;
    private Integer timeShiftMinutes;
    private String quantityHint;
    private List<Long> participantUserIds;

    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "reservationId", reservationId);
        put(values, "roomId", roomId);
        put(values, "roomName", roomName);
        put(values, "title", title);
        put(values, "meetingDate", meetingDate);
        put(values, "dateFrom", dateFrom);
        put(values, "dateTo", dateTo);
        put(values, "startClock", startClock);
        put(values, "endClock", endClock);
        put(values, "attendees", attendees);
        put(values, "rating", rating);
        put(values, "content", content);
        put(values, "deviceRequirements", deviceRequirements);
        put(values, "targetScope", targetScope);
        put(values, "timeRangeLabel", timeRangeLabel);
        put(values, "relativeTarget", relativeTarget);
        put(values, "mutationHint", mutationHint);
        put(values, "timeShiftMinutes", timeShiftMinutes);
        put(values, "quantityHint", quantityHint);
        put(values, "participantUserIds", participantUserIds);
        return values;
    }

    public void mergeFrom(AiAssistantIntentFields other) {
        if (other == null) {
            return;
        }
        reservationId = reservationId != null ? reservationId : other.reservationId;
        roomId = roomId != null ? roomId : other.roomId;
        roomName = roomName != null ? roomName : other.roomName;
        title = title != null ? title : other.title;
        meetingDate = meetingDate != null ? meetingDate : other.meetingDate;
        dateFrom = dateFrom != null ? dateFrom : other.dateFrom;
        dateTo = dateTo != null ? dateTo : other.dateTo;
        startClock = startClock != null ? startClock : other.startClock;
        endClock = endClock != null ? endClock : other.endClock;
        attendees = attendees != null ? attendees : other.attendees;
        rating = rating != null ? rating : other.rating;
        content = content != null ? content : other.content;
        deviceRequirements = deviceRequirements != null ? deviceRequirements : other.deviceRequirements;
        targetScope = targetScope != null ? targetScope : other.targetScope;
        timeRangeLabel = timeRangeLabel != null ? timeRangeLabel : other.timeRangeLabel;
        relativeTarget = relativeTarget != null ? relativeTarget : other.relativeTarget;
        mutationHint = mutationHint != null ? mutationHint : other.mutationHint;
        timeShiftMinutes = timeShiftMinutes != null ? timeShiftMinutes : other.timeShiftMinutes;
        quantityHint = quantityHint != null ? quantityHint : other.quantityHint;
        participantUserIds = participantUserIds != null ? participantUserIds : other.participantUserIds;
    }

    private void put(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }
}
