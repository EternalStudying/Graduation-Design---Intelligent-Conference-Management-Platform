package com.llf.service;

import com.llf.vo.NotificationItemVO;
import com.llf.vo.NotificationReadAllResultVO;
import com.llf.vo.NotificationReadResultVO;
import com.llf.vo.NotificationSummaryVO;
import com.llf.vo.NotificationTodoTargetVO;
import com.llf.vo.PageResultVO;

import java.util.List;

public interface NotificationService {
    NotificationSummaryVO getSummary(Long userId);

    PageResultVO<NotificationItemVO> list(Long userId, String category, Integer pageNum, Integer pageSize);

    NotificationReadResultVO markRead(Long userId, Long id);

    NotificationReadAllResultVO markAllRead(Long userId, String category);

    void createReservationCreatedNotification(Long userId, String reservationTitle, String roomName, String startTime, String endTime);

    void createReservationUpdatedNotification(Long userId, String reservationTitle, String roomName, String startTime, String endTime);

    void createReservationCancelledNotification(Long userId, String reservationTitle, String cancelReason);

    void createReviewTodoNotifications(List<NotificationTodoTargetVO> targets);
}
