package com.llf.controller;

import com.llf.auth.AuthContext;
import com.llf.dto.reservation.MyReservationCancelDTO;
import com.llf.dto.reservation.MyReservationReviewDTO;
import com.llf.dto.reservation.MyReservationUpdateDTO;
import com.llf.dto.reservation.ReservationCreateDTO;
import com.llf.dto.reservation.ReservationRecommendationDTO;
import com.llf.result.R;
import com.llf.service.ReservationService;
import com.llf.vo.reservation.CalendarEventVO;
import com.llf.vo.reservation.MyReservationReviewResultVO;
import com.llf.vo.reservation.MyReservationVO;
import com.llf.vo.common.PageResultVO;
import com.llf.vo.reservation.ReservationCreateVO;
import com.llf.vo.reservation.ReservationRecommendationVO;
import com.llf.vo.room.RoomOptionVO;
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

    @PostMapping("/recommendations")
    public R<ReservationRecommendationVO> recommendations(@Valid @RequestBody ReservationRecommendationDTO dto) {
        return R.ok(reservationService.recommend(dto));
    }

    @GetMapping("/my")
    public R<List<MyReservationVO>> my(@RequestParam @NotBlank(message = "startDate must not be blank") String startDate,
                                       @RequestParam @NotBlank(message = "endDate must not be blank") String endDate,
                                       @RequestParam @NotBlank(message = "scope must not be blank") String scope,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false, defaultValue = "false") boolean futureOnly) {
        Long currentUserId = AuthContext.get().getId();
        return R.ok(reservationService.myReservations(currentUserId, startDate, endDate, scope, status, futureOnly));
    }

    @GetMapping("/my/ended")
    public R<PageResultVO<MyReservationVO>> myEnded(@RequestParam(required = false) String scope,
                                                    @RequestParam(required = false) Integer pageNum,
                                                    @RequestParam(required = false) Integer pageSize) {
        Long currentUserId = AuthContext.get().getId();
        return R.ok(reservationService.myEndedReservations(currentUserId, scope, pageNum, pageSize));
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

    @PostMapping("/my/{id}/review")
    public R<MyReservationReviewResultVO> reviewMyReservation(@PathVariable Long id,
                                                              @Valid @RequestBody MyReservationReviewDTO dto) {
        Long currentUserId = AuthContext.get().getId();
        return R.ok(reservationService.submitMyReservationReview(id, currentUserId, dto));
    }
}
