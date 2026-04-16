package com.llf.controller;

import com.llf.mapper.RoomMapper;
import com.llf.result.R;
import com.llf.service.RoomService;
import com.llf.vo.room.RoomPageDataVO;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    @Resource
    private RoomService roomService;
    @Resource
    private RoomMapper roomMapper;

    @GetMapping
    public R<RoomPageDataVO> list(@RequestParam @NotNull(message = "currentPage must not be null") @Min(value = 1, message = "currentPage must be greater than 0") Integer currentPage,
                                  @RequestParam @NotNull(message = "size must not be null") @Min(value = 1, message = "size must be greater than 0") Integer size,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String capacityType,
                                  @RequestParam(required = false) String location) {
        return R.ok(roomService.pageRooms(currentPage, size, keyword, status, capacityType, location));
    }

    @GetMapping("/locations")
    public R<java.util.List<String>> locations() {
        return R.ok(roomMapper.selectLocations());
    }
}
