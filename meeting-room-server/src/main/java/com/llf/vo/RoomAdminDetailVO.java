package com.llf.vo;

import lombok.Data;
import java.util.List;

@Data
public class RoomAdminDetailVO {
    private String roomCode;
    private List<Long> deviceIds;
}