package com.llf.assistant.handler;

import com.llf.assistant.AiAssistantActionHandler;
import com.llf.assistant.AiAssistantActionPlan;
import com.llf.assistant.AiAssistantExecutionResult;
import com.llf.assistant.AiAssistantHelper;
import com.llf.assistant.AiAssistantSessionStore;
import com.llf.auth.AuthUser;
import com.llf.result.BizException;
import com.llf.service.ReservationService;
import com.llf.vo.admin.reservation.AdminReservationItemVO;
import com.llf.vo.admin.reservation.AdminReservationPageVO;
import com.llf.vo.assistant.AiAssistantFieldOptionVO;
import com.llf.vo.assistant.AiAssistantMissingFieldVO;
import com.llf.vo.assistant.AiAssistantResultVO;
import com.llf.vo.assistant.AiAssistantSummaryItemVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AdminReservationAssistantActionHandler implements AiAssistantActionHandler {

    private static final String ACTION_PENDING = "admin.reservations.pending";
    private static final String ACTION_APPROVE = "admin.reservations.approve";
    private static final String ACTION_REJECT = "admin.reservations.reject";
    private static final Pattern RESERVATION_ID_PATTERN = Pattern.compile("(\\d{3,})");

    private final ReservationService reservationService;

    public AdminReservationAssistantActionHandler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Override
    public List<String> supportedActionTypes() {
        return List.of(ACTION_PENDING, ACTION_APPROVE, ACTION_REJECT);
    }

    @Override
    public AiAssistantActionPlan process(String actionType, AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
        if (!isAdmin(currentUser)) {
            return AiAssistantActionPlan.error("该操作仅管理员可用。", defaultSuggestions(), errorResult("无管理员权限"));
        }
        return switch (actionType) {
            case ACTION_PENDING -> handlePending(session);
            case ACTION_APPROVE -> handleApprove(session, message);
            case ACTION_REJECT -> handleReject(session, message);
            default -> AiAssistantActionPlan.error("当前还不支持这个管理员动作。", defaultSuggestions(), errorResult("动作不支持"));
        };
    }

    @Override
    public AiAssistantExecutionResult execute(String actionType, AuthUser currentUser, Map<String, Object> params) {
        if (!isAdmin(currentUser)) {
            return AiAssistantExecutionResult.error("该操作仅管理员可用。", "无管理员权限", List.of());
        }
        Long reservationId = AiAssistantHelper.longValue(params, "reservationId");
        if (reservationId == null) {
            throw new BizException(400, "请选择目标预约");
        }
        if (ACTION_APPROVE.equals(actionType)) {
            AdminReservationItemVO approved = reservationService.adminApproveReservation(
                    reservationId,
                    currentUser.getId(),
                    AiAssistantHelper.stringValue(params, "remark")
            );
            return AiAssistantExecutionResult.success(
                    "预约已审核通过。",
                    "预约审核通过",
                    resultSummary(approved),
                    "/admin/reservations"
            );
        }
        if (ACTION_REJECT.equals(actionType)) {
            String reason = AiAssistantHelper.stringValue(params, "reason");
            if (reason == null) {
                throw new BizException(400, "请填写驳回原因");
            }
            AdminReservationItemVO rejected = reservationService.adminRejectReservation(reservationId, currentUser.getId(), reason);
            return AiAssistantExecutionResult.success(
                    "预约已驳回。",
                    "预约已驳回",
                    resultSummary(rejected),
                    "/admin/reservations"
            );
        }
        return AiAssistantExecutionResult.error("这个动作不需要执行。", "动作无需执行", List.of());
    }

    private AiAssistantActionPlan handlePending(AiAssistantSessionStore.Session session) {
        AdminReservationPageVO page = reservationService.adminReservations(1, 10, null, "PENDING");
        List<AdminReservationItemVO> reservations = page == null || page.getList() == null ? List.of() : page.getList();
        if (reservations.isEmpty()) {
            return AiAssistantActionPlan.reply("当前没有待审核预约。", List.of("查看管理端预约审核", "查看今日概览"));
        }
        session.setLastReservationId(reservations.get(0).getId());

        StringBuilder builder = new StringBuilder("当前有 ").append(page.getTotal() == null ? reservations.size() : page.getTotal()).append(" 条待审核预约");
        for (int i = 0; i < Math.min(3, reservations.size()); i++) {
            AdminReservationItemVO item = reservations.get(i);
            builder.append(i == 0 ? "：" : "；")
                    .append(item.getTitle())
                    .append("（")
                    .append(item.getOrganizerName())
                    .append("，")
                    .append(item.getRoomName())
                    .append("，")
                    .append(shortTimeRange(item))
                    .append("，ID ")
                    .append(item.getId())
                    .append("）");
        }
        return AiAssistantActionPlan.reply(builder.toString(), List.of("通过预约 " + reservations.get(0).getId(), "驳回预约 " + reservations.get(0).getId()));
    }

    private AiAssistantActionPlan handleApprove(AiAssistantSessionStore.Session session, String message) {
        Map<String, Object> draft = session.getDraft();
        putReservationIdIfPresent(draft, message);
        Long reservationId = resolveReservationId(session, draft);
        if (reservationId == null) {
            return collectReservation("请选择要通过的预约。");
        }
        draft.put("reservationId", reservationId);
        return AiAssistantActionPlan.confirm(
                "已定位到目标预约，请确认是否审核通过。",
                List.of("确认执行", "取消本次操作"),
                "通过预约",
                List.of(AiAssistantHelper.summary("预约 ID", String.valueOf(reservationId))),
                Map.copyOf(draft)
        );
    }

    private AiAssistantActionPlan handleReject(AiAssistantSessionStore.Session session, String message) {
        Map<String, Object> draft = session.getDraft();
        putReservationIdIfPresent(draft, message);
        putReasonIfPresent(draft, message);
        Long reservationId = resolveReservationId(session, draft);
        if (reservationId == null) {
            return collectReservation("请选择要驳回的预约。");
        }
        draft.put("reservationId", reservationId);
        String reason = AiAssistantHelper.stringValue(draft, "reason");
        if (reason == null) {
            return AiAssistantActionPlan.collect(
                    "驳回预约前，请补充驳回原因。",
                    List.of("时间冲突，无法安排", "信息不完整，请重新提交"),
                    List.of(AiAssistantHelper.field("reason", "驳回原因", "textarea", true, "例如：时间冲突，无法安排", null, List.of()))
            );
        }
        return AiAssistantActionPlan.confirm(
                "已整理驳回信息，请确认是否执行。",
                List.of("确认执行", "取消本次操作"),
                "驳回预约",
                List.of(
                        AiAssistantHelper.summary("预约 ID", String.valueOf(reservationId)),
                        AiAssistantHelper.summary("驳回原因", reason)
                ),
                Map.copyOf(draft)
        );
    }

    private AiAssistantActionPlan collectReservation(String text) {
        List<AiAssistantFieldOptionVO> options = pendingReservationOptions();
        List<AiAssistantMissingFieldVO> fields = List.of(
                AiAssistantHelper.field("reservationId", "目标预约", "select", true, "请选择预约", null, options)
        );
        return AiAssistantActionPlan.collect(text, List.of("查看待审核预约"), fields);
    }

    private List<AiAssistantFieldOptionVO> pendingReservationOptions() {
        AdminReservationPageVO page = reservationService.adminReservations(1, 10, null, "PENDING");
        List<AdminReservationItemVO> reservations = page == null || page.getList() == null ? List.of() : page.getList();
        return reservations.stream()
                .map(item -> AiAssistantHelper.option(item.getTitle() + " | " + item.getRoomName() + " | ID " + item.getId(), item.getId()))
                .toList();
    }

    private Long resolveReservationId(AiAssistantSessionStore.Session session, Map<String, Object> draft) {
        Long reservationId = AiAssistantHelper.longValue(draft, "reservationId");
        if (reservationId != null) {
            return reservationId;
        }
        return session == null ? null : session.getLastReservationId();
    }

    private void putReservationIdIfPresent(Map<String, Object> draft, String message) {
        Long reservationId = parseReservationId(message);
        if (reservationId != null) {
            draft.put("reservationId", reservationId);
        }
    }

    private Long parseReservationId(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = RESERVATION_ID_PATTERN.matcher(message);
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private void putReasonIfPresent(Map<String, Object> draft, String message) {
        String reason = parseRejectReason(message);
        if (reason != null) {
            draft.put("reason", reason);
        }
    }

    private String parseRejectReason(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        for (String marker : List.of("原因", "理由", "因为")) {
            int index = message.indexOf(marker);
            if (index >= 0 && index + marker.length() < message.length()) {
                String value = message.substring(index + marker.length()).replaceFirst("^[：:，,\\s]+", "").trim();
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private List<AiAssistantSummaryItemVO> resultSummary(AdminReservationItemVO item) {
        if (item == null) {
            return List.of();
        }
        return List.of(
                AiAssistantHelper.summary("预约 ID", String.valueOf(item.getId())),
                AiAssistantHelper.summary("会议主题", item.getTitle() == null ? "" : item.getTitle())
        );
    }

    private String shortTimeRange(AdminReservationItemVO item) {
        String start = item.getStartTime() == null || item.getStartTime().length() < 16 ? item.getStartTime() : item.getStartTime().substring(11, 16);
        String end = item.getEndTime() == null || item.getEndTime().length() < 16 ? item.getEndTime() : item.getEndTime().substring(11, 16);
        return start + "-" + end;
    }

    private boolean isAdmin(AuthUser user) {
        if (user == null || user.getRole() == null || user.getRole().isBlank()) {
            return false;
        }
        String role = user.getRole().trim();
        return "ADMIN".equalsIgnoreCase(role) || "2".equals(role) || "admin".equalsIgnoreCase(role);
    }

    private AiAssistantResultVO errorResult(String title) {
        AiAssistantResultVO result = new AiAssistantResultVO();
        result.setStatus("error");
        result.setTitle(title);
        return result;
    }

    private List<String> defaultSuggestions() {
        return List.of("查看待审核预约", "查看管理端预约审核");
    }
}
