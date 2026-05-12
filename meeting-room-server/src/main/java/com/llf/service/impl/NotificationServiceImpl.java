package com.llf.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llf.mapper.NotificationMapper;
import com.llf.result.BizException;
import com.llf.service.NotificationService;
import com.llf.vo.notification.AdminNotificationPublishVO;
import com.llf.vo.notification.NotificationItemVO;
import com.llf.vo.notification.NotificationReadAllResultVO;
import com.llf.vo.notification.NotificationReadResultVO;
import com.llf.vo.notification.NotificationSummaryVO;
import com.llf.vo.notification.NotificationTodoTargetVO;
import com.llf.vo.common.PageResultVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final List<String> SUPPORTED_CATEGORIES = List.of("NOTICE", "MESSAGE", "TODO");
    private static final List<String> SUPPORTED_ADMIN_NOTIFICATION_TYPES = List.of("ANNOUNCEMENT", "MAINTENANCE");
    private static final List<String> SUPPORTED_RECIPIENT_SCOPES = List.of("ALL", "USERS", "ADMINS");
    private static final String DEFAULT_ROUTE = "/reservations/index";
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private NotificationMapper notificationMapper;

    @Override
    public NotificationSummaryVO getSummary(Long userId) {
        Map<String, Integer> unreadByCategory = initUnreadMap();
        int totalUnread = 0;
        for (NotificationMapper.CategoryUnreadRow row : notificationMapper.countUnreadByCategory(userId)) {
            int unreadCount = defaultZero(row.getUnreadCount());
            unreadByCategory.put(row.getCategory(), unreadCount);
            totalUnread += unreadCount;
        }

        NotificationSummaryVO vo = new NotificationSummaryVO();
        vo.setTotalUnread(totalUnread);
        vo.setUnreadByCategory(unreadByCategory);
        return vo;
    }

    @Override
    public PageResultVO<NotificationItemVO> list(Long userId, String category, Integer pageNum, Integer pageSize) {
        String normalizedCategory = normalizeCategory(category, true);
        int resolvedPageNum = resolvePageNum(pageNum);
        int resolvedPageSize = resolvePageSize(pageSize);
        int offset = (resolvedPageNum - 1) * resolvedPageSize;

        long total = notificationMapper.countNotifications(userId, normalizedCategory);
        List<NotificationItemVO> list = total <= 0
                ? List.of()
                : notificationMapper.selectNotifications(userId, normalizedCategory, resolvedPageSize, offset)
                .stream()
                .map(this::toNotificationItemVO)
                .toList();

        PageResultVO<NotificationItemVO> result = new PageResultVO<>();
        result.setList(list);
        result.setTotal(total);
        result.setPageNum(resolvedPageNum);
        result.setPageSize(resolvedPageSize);
        return result;
    }

    @Override
    @Transactional
    public NotificationReadResultVO markRead(Long userId, Long id) {
        NotificationMapper.NotificationReadRow row = notificationMapper.selectNotificationReadState(userId, id);
        if (row == null) {
            throw new BizException(404, "notification not found");
        }
        if (!Boolean.TRUE.equals(row.getReadFlag())) {
            notificationMapper.markRead(userId, id);
        }

        NotificationReadResultVO vo = new NotificationReadResultVO();
        vo.setId(id);
        vo.setRead(Boolean.TRUE);
        return vo;
    }

    @Override
    @Transactional
    public NotificationReadAllResultVO markAllRead(Long userId, String category) {
        String normalizedCategory = normalizeCategory(category, false);
        int updatedCount = notificationMapper.markAllReadByCategory(userId, normalizedCategory);

        NotificationReadAllResultVO vo = new NotificationReadAllResultVO();
        vo.setCategory(normalizedCategory);
        vo.setUpdatedCount(updatedCount);
        return vo;
    }

    @Override
    @Transactional
    public AdminNotificationPublishVO publishAdminNotification(String type, String title, String content, String recipientScope) {
        String normalizedType = normalizeAdminNotificationType(type);
        String normalizedScope = normalizeRecipientScope(recipientScope);
        String normalizedTitle = normalizeText(title, "title", 128);
        String normalizedContent = normalizeText(content, "content", 500);
        String extra = "MAINTENANCE".equals(normalizedType) ? "维护通知" : "系统公告";
        String status = "MAINTENANCE".equals(normalizedType) ? "warning" : "primary";
        List<Long> recipientIds = notificationMapper.selectActiveNotificationRecipientIds(normalizedScope);

        for (Long recipientId : recipientIds) {
            insertNotification(
                    recipientId,
                    "NOTICE",
                    normalizedTitle,
                    normalizedContent,
                    null,
                    null,
                    extra,
                    status
            );
        }

        AdminNotificationPublishVO vo = new AdminNotificationPublishVO();
        vo.setType(normalizedType);
        vo.setCategory("NOTICE");
        vo.setRecipientScope(normalizedScope);
        vo.setTitle(normalizedTitle);
        vo.setPublishedCount(recipientIds.size());
        return vo;
    }

    @Override
    public void createReservationCreatedNotification(Long userId, String reservationTitle, String roomName, String startTime, String endTime) {
        insertNotification(
                userId,
                "NOTICE",
                "预约创建成功",
                "您已成功预约" + wrapTitle(reservationTitle) + buildReservationTimeSuffix(roomName, startTime, endTime),
                DEFAULT_ROUTE,
                null,
                null,
                "success"
        );
    }

    @Override
    public void createReservationUpdatedNotification(Long userId, String reservationTitle, String roomName, String startTime, String endTime) {
        insertNotification(
                userId,
                "MESSAGE",
                "预约已修改",
                wrapTitle(reservationTitle) + "已更新" + buildReservationTimeSuffix(roomName, startTime, endTime),
                DEFAULT_ROUTE,
                null,
                null,
                "info"
        );
    }

    @Override
    public void createReservationCancelledNotification(Long userId, String reservationTitle, String cancelReason) {
        String content = wrapTitle(reservationTitle) + "已取消";
        if (cancelReason != null && !cancelReason.isBlank()) {
            content += "，原因：" + cancelReason;
        }
        insertNotification(
                userId,
                "MESSAGE",
                "预约已取消",
                content,
                DEFAULT_ROUTE,
                null,
                null,
                "warning"
        );
    }

    @Override
    public void createReservationApprovedNotification(Long userId, String reservationTitle, String roomName, String startTime, String endTime) {
        insertNotification(
                userId,
                "NOTICE",
                "预约审核通过",
                wrapTitle(reservationTitle) + "已审核通过" + buildReservationTimeSuffix(roomName, startTime, endTime),
                DEFAULT_ROUTE,
                null,
                null,
                "success"
        );
    }

    @Override
    public void createReservationRejectedNotification(Long userId, String reservationTitle, String rejectReason) {
        insertNotification(
                userId,
                "MESSAGE",
                "预约已驳回",
                wrapTitle(reservationTitle) + "已驳回，原因：" + rejectReason,
                DEFAULT_ROUTE,
                null,
                null,
                "warning"
        );
    }

    @Override
    public void createReservationExceptionNotification(Long userId, String reservationTitle, String exceptionReason) {
        insertNotification(
                userId,
                "MESSAGE",
                "预约已标记异常",
                wrapTitle(reservationTitle) + "已被标记为异常，原因：" + exceptionReason,
                DEFAULT_ROUTE,
                null,
                null,
                "danger"
        );
    }

    @Override
    public void createReviewTodoNotifications(List<NotificationTodoTargetVO> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (NotificationTodoTargetVO target : targets) {
            if (target == null || target.getUserId() == null) {
                continue;
            }
            insertNotification(
                    target.getUserId(),
                    "TODO",
                    "会后待评价",
                    "会议" + wrapTitle(target.getTitle()) + "已结束，请及时完成评价。",
                    DEFAULT_ROUTE,
                    null,
                    null,
                    "primary"
            );
        }
    }

    private NotificationItemVO toNotificationItemVO(NotificationMapper.NotificationRow row) {
        NotificationItemVO vo = new NotificationItemVO();
        vo.setId(row.getId());
        vo.setCategory(row.getCategory());
        vo.setTitle(row.getTitle());
        vo.setContent(row.getContent());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setRead(Boolean.TRUE.equals(row.getReadFlag()));
        vo.setRoute(row.getRoute());
        vo.setRouteQuery(parseRouteQuery(row.getRouteQueryText()));
        vo.setExtra(row.getExtra());
        vo.setStatus(row.getStatus());
        return vo;
    }

    private Object parseRouteQuery(String routeQueryText) {
        if (routeQueryText == null || routeQueryText.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(routeQueryText, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignore) {
            return null;
        }
    }

    private void insertNotification(Long userId,
                                    String category,
                                    String title,
                                    String content,
                                    String route,
                                    String routeQuery,
                                    String extra,
                                    String status) {
        notificationMapper.insertNotification(userId, category, title, content, route, routeQuery, extra, status);
    }

    private String normalizeCategory(String category, boolean allowNull) {
        if (category == null || category.isBlank()) {
            if (allowNull) {
                return null;
            }
            throw new BizException(400, "category must be one of NOTICE, MESSAGE, TODO");
        }
        String normalized = category.trim().toUpperCase();
        if (!SUPPORTED_CATEGORIES.contains(normalized)) {
            throw new BizException(400, "category must be one of NOTICE, MESSAGE, TODO");
        }
        return normalized;
    }

    private String normalizeAdminNotificationType(String type) {
        if (type == null || type.isBlank()) {
            throw new BizException(400, "type must be one of ANNOUNCEMENT, MAINTENANCE");
        }
        String normalized = type.trim().toUpperCase();
        if (!SUPPORTED_ADMIN_NOTIFICATION_TYPES.contains(normalized)) {
            throw new BizException(400, "type must be one of ANNOUNCEMENT, MAINTENANCE");
        }
        return normalized;
    }

    private String normalizeRecipientScope(String recipientScope) {
        if (recipientScope == null || recipientScope.isBlank()) {
            return "ALL";
        }
        String normalized = recipientScope.trim().toUpperCase();
        if (!SUPPORTED_RECIPIENT_SCOPES.contains(normalized)) {
            throw new BizException(400, "recipientScope must be one of ALL, USERS, ADMINS");
        }
        return normalized;
    }

    private String normalizeText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BizException(400, fieldName + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BizException(400, fieldName + " length must not exceed " + maxLength);
        }
        return normalized;
    }

    private Map<String, Integer> initUnreadMap() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String category : SUPPORTED_CATEGORIES) {
            result.put(category, 0);
        }
        return result;
    }

    private int resolvePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return DEFAULT_PAGE_NUM;
        }
        return pageNum;
    }

    private int resolvePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private int defaultZero(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private String wrapTitle(String reservationTitle) {
        if (reservationTitle == null || reservationTitle.isBlank()) {
            return "您的预约";
        }
        return "《" + reservationTitle + "》";
    }

    private String buildReservationTimeSuffix(String roomName, String startTime, String endTime) {
        StringBuilder content = new StringBuilder();
        if (roomName != null && !roomName.isBlank()) {
            content.append("，会议室：").append(roomName);
        }
        if (startTime != null && !startTime.isBlank() && endTime != null && !endTime.isBlank()) {
            content.append("，时间：").append(startTime).append(" - ").append(endTime);
        }
        return content.toString();
    }
}
