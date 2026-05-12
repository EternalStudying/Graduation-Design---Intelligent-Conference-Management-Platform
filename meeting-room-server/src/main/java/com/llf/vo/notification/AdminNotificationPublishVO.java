package com.llf.vo.notification;

import lombok.Data;

@Data
public class AdminNotificationPublishVO {
    private String type;
    private String category;
    private String recipientScope;
    private String title;
    private Integer publishedCount;
}
