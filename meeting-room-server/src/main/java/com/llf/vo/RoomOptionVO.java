package com.llf.vo;

import lombok.Data;

@Data
public class RoomOptionVO {
    private Long id;
    private String roomCode;
    private String name;
    private String location;
    private Integer capacity;
    private String status;
    private String description;
}
