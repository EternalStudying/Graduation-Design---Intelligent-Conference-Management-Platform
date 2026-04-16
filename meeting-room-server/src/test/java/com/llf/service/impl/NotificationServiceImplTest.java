package com.llf.service.impl;

import com.llf.mapper.NotificationMapper;
import com.llf.result.BizException;
import com.llf.vo.NotificationItemVO;
import com.llf.vo.NotificationReadAllResultVO;
import com.llf.vo.NotificationReadResultVO;
import com.llf.vo.NotificationSummaryVO;
import com.llf.vo.PageResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void getSummary_shouldFillMissingCategoriesWithZero() {
        when(notificationMapper.countUnreadByCategory(1L)).thenReturn(List.of(
                unreadRow("NOTICE", 1L),
                unreadRow("TODO", 2L)
        ));

        NotificationSummaryVO result = notificationService.getSummary(1L);

        assertEquals(3, result.getTotalUnread());
        assertEquals(Map.of("NOTICE", 1, "MESSAGE", 0, "TODO", 2), result.getUnreadByCategory());
    }

    @Test
    void list_shouldReturnPagedNotifications() {
        when(notificationMapper.countNotifications(1L, "NOTICE")).thenReturn(1L);
        when(notificationMapper.selectNotifications(1L, "NOTICE", 20, 0)).thenReturn(List.of(
                notificationRow(1001L, "NOTICE", "会议时间已调整", "周会复盘已调整到今天 16:30，请留意最新时间。", "2026-04-16 09:00:00", false, "/reservations/index", null, null, null)
        ));

        PageResultVO<NotificationItemVO> result = notificationService.list(1L, "NOTICE", 1, 20);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(20, result.getPageSize());
        assertEquals(1, result.getList().size());
        assertEquals("NOTICE", result.getList().get(0).getCategory());
        assertEquals(Boolean.FALSE, result.getList().get(0).getRead());
        assertNull(result.getList().get(0).getRouteQuery());
    }

    @Test
    void markRead_shouldUpdateUnreadNotification() {
        when(notificationMapper.selectNotificationReadState(1L, 1001L)).thenReturn(readRow(1001L, false));

        NotificationReadResultVO result = notificationService.markRead(1L, 1001L);

        assertEquals(1001L, result.getId());
        assertEquals(Boolean.TRUE, result.getRead());
        verify(notificationMapper).markRead(1L, 1001L);
    }

    @Test
    void markRead_shouldRejectWhenNotificationDoesNotExist() {
        when(notificationMapper.selectNotificationReadState(1L, 1002L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> notificationService.markRead(1L, 1002L));

        assertEquals(404, ex.getCode());
        verify(notificationMapper, never()).markRead(1L, 1002L);
    }

    @Test
    void markAllRead_shouldOnlyUpdateSpecifiedCategory() {
        when(notificationMapper.markAllReadByCategory(1L, "NOTICE")).thenReturn(3);

        NotificationReadAllResultVO result = notificationService.markAllRead(1L, "NOTICE");

        assertEquals("NOTICE", result.getCategory());
        assertEquals(3, result.getUpdatedCount());
    }

    @Test
    void createReservationCreatedNotification_shouldInsertNoticeMessage() {
        notificationService.createReservationCreatedNotification(1L, "周会复盘", "晨光会议室", "2026-04-16 09:00:00", "2026-04-16 10:00:00");

        verify(notificationMapper).insertNotification(
                1L,
                "NOTICE",
                "预约创建成功",
                "您已成功预约《周会复盘》，会议室：晨光会议室，时间：2026-04-16 09:00:00 - 2026-04-16 10:00:00",
                "/reservations/index",
                null,
                null,
                "success"
        );
    }

    @Test
    void list_shouldRejectInvalidCategory() {
        BizException ex = assertThrows(BizException.class, () -> notificationService.list(1L, "UNKNOWN", 1, 20));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("category"));
    }

    private NotificationMapper.CategoryUnreadRow unreadRow(String category, Long unreadCount) {
        NotificationMapper.CategoryUnreadRow row = new NotificationMapper.CategoryUnreadRow();
        row.setCategory(category);
        row.setUnreadCount(unreadCount);
        return row;
    }

    private NotificationMapper.NotificationRow notificationRow(Long id,
                                                               String category,
                                                               String title,
                                                               String content,
                                                               String createdAt,
                                                               boolean read,
                                                               String route,
                                                               String routeQueryText,
                                                               String extra,
                                                               String status) {
        NotificationMapper.NotificationRow row = new NotificationMapper.NotificationRow();
        row.setId(id);
        row.setCategory(category);
        row.setTitle(title);
        row.setContent(content);
        row.setCreatedAt(createdAt);
        row.setReadFlag(read);
        row.setRoute(route);
        row.setRouteQueryText(routeQueryText);
        row.setExtra(extra);
        row.setStatus(status);
        return row;
    }

    private NotificationMapper.NotificationReadRow readRow(Long id, boolean read) {
        NotificationMapper.NotificationReadRow row = new NotificationMapper.NotificationReadRow();
        row.setId(id);
        row.setReadFlag(read);
        return row;
    }
}
