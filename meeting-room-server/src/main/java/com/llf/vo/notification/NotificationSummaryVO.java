package com.llf.vo.notification;

import lombok.Data;

import java.util.Map;

@Data
public class NotificationSummaryVO {
    private Integer totalUnread;
    private Map<String, Integer> unreadByCategory;
}

