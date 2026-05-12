package com.llf.service;

import com.llf.dto.reservation.MyReservationCancelDTO;
import com.llf.dto.reservation.MyReservationReviewDTO;
import com.llf.dto.reservation.MyReservationUpdateDTO;
import com.llf.dto.reservation.ReservationCreateDTO;
import com.llf.dto.reservation.ReservationRecommendationDTO;
import com.llf.vo.reservation.CalendarEventVO;
import com.llf.vo.reservation.MyReservationReviewResultVO;
import com.llf.vo.reservation.MyReservationVO;
import com.llf.vo.admin.reservation.AdminReservationItemVO;
import com.llf.vo.admin.reservation.AdminReservationPageVO;
import com.llf.vo.common.PageResultVO;
import com.llf.vo.reservation.ReservationCreateVO;
import com.llf.vo.reservation.ReservationRecommendationVO;
import com.llf.vo.room.RoomOptionVO;

import java.util.List;

public interface ReservationService {
    List<CalendarEventVO> listCalendar(String startDate, String endDate, Long roomId, String status);

    ReservationCreateVO create(ReservationCreateDTO dto, Long organizerId);

    ReservationRecommendationVO recommend(ReservationRecommendationDTO dto);

    List<MyReservationVO> myReservations(Long currentUserId, String startDate, String endDate, String scope, String status, boolean futureOnly);

    MyReservationVO myReservationDetail(Long id, Long currentUserId);

    PageResultVO<MyReservationVO> myEndedReservations(Long currentUserId, String scope, Integer pageNum, Integer pageSize);

    List<RoomOptionVO> myRoomOptions();

    MyReservationVO updateMyReservation(Long id, Long currentUserId, MyReservationUpdateDTO dto);

    MyReservationVO cancelMyReservation(Long id, Long currentUserId, MyReservationCancelDTO dto);

    MyReservationReviewResultVO submitMyReservationReview(Long id, Long currentUserId, MyReservationReviewDTO dto);

    AdminReservationPageVO adminReservations(Integer currentPage, Integer size, String keyword, String status);

    AdminReservationItemVO adminApproveReservation(Long id, Long adminUserId, String remark);

    AdminReservationItemVO adminRejectReservation(Long id, Long adminUserId, String reason);

    AdminReservationItemVO adminExceptionReservation(Long id, Long adminUserId, String reason);

    int markEnded();
}
