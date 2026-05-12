package com.llf.controller;

import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.dto.admin.notification.AdminNotificationPublishDTO;
import com.llf.result.BizException;
import com.llf.result.R;
import com.llf.service.NotificationService;
import com.llf.vo.notification.AdminNotificationPublishVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {

    @Resource
    private NotificationService notificationService;

    @PostMapping
    public R<AdminNotificationPublishVO> publish(@Valid @RequestBody AdminNotificationPublishDTO dto) {
        requireAdmin();
        return R.ok(notificationService.publishAdminNotification(
                dto.getType(),
                dto.getTitle(),
                dto.getContent(),
                dto.getRecipientScope()
        ));
    }

    private AuthUser requireAdmin() {
        AuthUser user = AuthContext.get();
        if (user == null || !isAdminRole(user.getRole())) {
            throw new BizException(403, "admin permission required");
        }
        return user;
    }

    private boolean isAdminRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String value = role.trim();
        return "ADMIN".equalsIgnoreCase(value) || "2".equals(value) || "admin".equalsIgnoreCase(value);
    }
}
