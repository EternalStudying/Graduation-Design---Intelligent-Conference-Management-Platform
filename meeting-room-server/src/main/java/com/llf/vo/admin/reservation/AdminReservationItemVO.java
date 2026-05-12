package com.llf.vo.admin.reservation;

import com.llf.vo.reservation.MyReservationVO;
import com.llf.vo.user.UserOptionVO;
import lombok.Data;

import java.util.List;

@Data
public class AdminReservationItemVO {
    private Long id;
    private String reservationNo;
    private Long roomId;
    private String roomCode;
    private String roomName;
    private String roomLocation;
    private Integer roomCapacity;
    private Long organizerId;
    private String organizerName;
    private String title;
    private Integer attendees;
    private String startTime;
    private String endTime;
    private String status;
    private String remark;
    private String cancelReason;
    private String approvalRemark;
    private String rejectReason;
    private String exceptionReason;
    private String processedByName;
    private String processedAt;
    private String createdAt;
    private List<MyReservationVO.DeviceVO> devices;
    private List<UserOptionVO> participants;
}
