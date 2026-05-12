package com.llf.controller;

import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.dto.admin.reservation.AdminReservationApproveDTO;
import com.llf.dto.admin.reservation.AdminReservationReasonDTO;
import com.llf.result.BizException;
import com.llf.result.R;
import com.llf.service.ReservationService;
import com.llf.vo.admin.reservation.AdminReservationItemVO;
import com.llf.vo.admin.reservation.AdminReservationPageVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/admin/reservations")
public class AdminReservationController {

    @Resource
    private ReservationService reservationService;

    @GetMapping
    public R<AdminReservationPageVO> page(@RequestParam @NotNull(message = "currentPage must not be null") @Min(value = 1, message = "currentPage must be greater than 0") Integer currentPage,
                                          @RequestParam @NotNull(message = "size must not be null") @Min(value = 1, message = "size must be greater than 0") Integer size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String status) {
        requireAdmin();
        return R.ok(reservationService.adminReservations(currentPage, size, keyword, status));
    }

    @PatchMapping("/{id}/approve")
    public R<AdminReservationItemVO> approve(@PathVariable Long id,
                                             @RequestBody(required = false) AdminReservationApproveDTO dto) {
        AuthUser admin = requireAdmin();
        return R.ok(reservationService.adminApproveReservation(id, admin.getId(), dto == null ? null : dto.getRemark()));
    }

    @PatchMapping("/{id}/reject")
    public R<AdminReservationItemVO> reject(@PathVariable Long id,
                                            @Valid @RequestBody AdminReservationReasonDTO dto) {
        AuthUser admin = requireAdmin();
        return R.ok(reservationService.adminRejectReservation(id, admin.getId(), dto.getReason()));
    }

    @PatchMapping("/{id}/exception")
    public R<AdminReservationItemVO> exception(@PathVariable Long id,
                                               @Valid @RequestBody AdminReservationReasonDTO dto) {
        AuthUser admin = requireAdmin();
        return R.ok(reservationService.adminExceptionReservation(id, admin.getId(), dto.getReason()));
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
