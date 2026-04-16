package com.llf.vo;

import lombok.Data;

import java.util.Map;

@Data
public class NotificationSummaryVO {
    private Integer totalUnread;
    private Map<String, Integer> unreadByCategory;
}
