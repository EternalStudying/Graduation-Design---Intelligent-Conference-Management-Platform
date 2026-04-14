package com.llf.controller;

import com.llf.auth.AuthContext;
import com.llf.dto.MyReservationCancelDTO;
import com.llf.dto.MyReservationUpdateDTO;
import com.llf.dto.ReservationCreateDTO;
import com.llf.mapper.ReservationMapper;
import com.llf.result.R;
import com.llf.service.ReservationService;
import com.llf.vo.CalendarEventVO;
import com.llf.vo.MyReservationVO;
import com.llf.vo.ReservationCreateVO;
import com.llf.vo.RoomOptionVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    @Resource
    private ReservationService reservationService;
    @Resource
    private ReservationMapper reservationMapper;

    @GetMapping("/calendar")
    public R<List<CalendarEventVO>> calendar(@RequestParam @NotBlank(message = "startDate must not be blank") String startDate,
                                             @RequestParam @NotBlank(message = "endDate must not be blank") String endDate,
                                             @RequestParam(required = false) Long roomId,
                                             @RequestParam(required = false) String status) {
        return R.ok(reservationService.listCalendar(startDate, endDate, roomId, status));
    }

    @PostMapping
    public R<ReservationCreateVO> create(@Valid @RequestBody ReservationCreateDTO dto) {
        Long organizerId = AuthContext.get().getId();
        return R.ok(reservationService.create(dto, organizerId));
    }

    @PostMapping("/{id}/cancel")
    public R<String> cancel(@PathVariable Long id) {
        reservationService.cancel(id);
        return R.ok("ok");
    }

    @GetMapping("/my")
    public R<List<MyReservationVO>> my(@RequestParam @NotBlank(message = "startDate must not be blank") String startDate,
                                       @RequestParam @NotBlank(message = "endDate must not be blank") String endDate,
                                       @RequestParam @NotBlank(message = "scope must not be blank") String scope,
                                       @RequestParam(required = false) String status) {
        Long currentUserId = AuthContext.get().getId();
        return R.ok(reservationService.myReservations(currentUserId, startDate, endDate, scope, status));
    }

    @GetMapping("/my/room-options")
    public R<List<RoomOptionVO>> myRoomOptions() {
        return R.ok(reservationService.myRoomOptions());
    }

    @PutMapping("/my/{id}")
    public R<MyReservationVO> updateMyReservation(@PathVariable Long id,
                                                  @Valid @RequestBody MyReservationUpdateDTO dto) {
        Long currentUserId = AuthContext.get().getId();
        return R.ok(reservationService.updateMyReservation(id, currentUserId, dto));
    }

    @PatchMapping("/my/{id}/cancel")
    public R<MyReservationVO> cancelMyReservation(@PathVariable Long id,
                                                  @Valid @RequestBody MyReservationCancelDTO dto) {
        Long currentUserId = AuthContext.get().getId();
        return R.ok(reservationService.cancelMyReservation(id, currentUserId, dto));
    }

    @GetMapping("/active-count")
    public R<Integer> activeCount() {
        return R.ok(reservationMapper.countActive());
    }
}
