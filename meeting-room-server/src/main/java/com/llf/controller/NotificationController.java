package com.llf.controller;

import com.llf.auth.AuthContext;
import com.llf.dto.NotificationReadAllDTO;
import com.llf.result.R;
import com.llf.service.NotificationService;
import com.llf.vo.NotificationItemVO;
import com.llf.vo.NotificationReadAllResultVO;
import com.llf.vo.NotificationReadResultVO;
import com.llf.vo.NotificationSummaryVO;
import com.llf.vo.PageResultVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    @Resource
    private NotificationService notificationService;

    @GetMapping("/summary")
    public R<NotificationSummaryVO> summary() {
        Long currentUserId = AuthContext.get().getId();
        return R.ok(notificationService.getSummary(currentUserId));
    }

    @GetMapping
    public R<PageResultVO<NotificationItemVO>> list(@RequestParam(required = false) String category,
                                                    @RequestParam(required = false) Integer pageNum,
                                                    @RequestParam(required = false) Integer pageSize) {
        Long currentUserId = AuthContext.get().getId();
        return R.ok(notificationService.list(currentUserId, category, pageNum, pageSize));
    }

    @PostMapping("/{id}/read")
    public R<NotificationReadResultVO> markRead(@PathVariable Long id) {
        Long currentUserId = AuthContext.get().getId();
        return R.ok(notificationService.markRead(currentUserId, id));
    }

    @PostMapping("/read-all")
    public R<NotificationReadAllResultVO> markAllRead(@Valid @RequestBody NotificationReadAllDTO dto) {
        Long currentUserId = AuthContext.get().getId();
        return R.ok(notificationService.markAllRead(currentUserId, dto.getCategory()));
    }
}
