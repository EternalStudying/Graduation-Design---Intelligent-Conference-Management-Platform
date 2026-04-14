package com.llf.service;

import com.llf.dto.MyReservationCancelDTO;
import com.llf.dto.MyReservationUpdateDTO;
import com.llf.dto.ReservationCreateDTO;
import com.llf.vo.CalendarEventVO;
import com.llf.vo.MyReservationVO;
import com.llf.vo.ReservationCreateVO;
import com.llf.vo.RoomOptionVO;

import java.util.List;

public interface ReservationService {
    List<CalendarEventVO> listCalendar(String startDate, String endDate, Long roomId, String status);

    ReservationCreateVO create(ReservationCreateDTO dto, Long organizerId);

    void cancel(Long id);

    List<MyReservationVO> myReservations(Long currentUserId, String startDate, String endDate, String scope, String status);

    List<RoomOptionVO> myRoomOptions();

    MyReservationVO updateMyReservation(Long id, Long currentUserId, MyReservationUpdateDTO dto);

    MyReservationVO cancelMyReservation(Long id, Long currentUserId, MyReservationCancelDTO dto);

    int markEnded();
}
