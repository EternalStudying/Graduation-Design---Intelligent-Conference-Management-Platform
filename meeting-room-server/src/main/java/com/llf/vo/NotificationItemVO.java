package com.llf.vo;

import lombok.Data;

@Data
public class NotificationItemVO {
    private Long id;
    private String category;
    private String title;
    private String content;
    private String createdAt;
    private Boolean read;
    private String route;
    private Object routeQuery;
    private String extra;
    private String status;
}
