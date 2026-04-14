package com.llf.controller;

import com.llf.dto.RoomUpsertDTO;
import com.llf.mapper.RoomMapper;
import com.llf.result.R;
import com.llf.service.RoomRecommendService;
import com.llf.service.RoomService;
import com.llf.vo.RoomAdminDetailVO;
import com.llf.vo.RoomListItemVO;
import com.llf.vo.RoomOptionVO;
import com.llf.vo.RoomPageDataVO;
import com.llf.vo.RoomRecommendItemVO;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    @Resource
    private RoomService roomService;
    @Resource
    private RoomMapper roomMapper;
    @Resource
    private RoomRecommendService roomRecommendService;

    @GetMapping
    public R<RoomPageDataVO> list(@RequestParam @NotNull(message = "currentPage must not be null") @Min(value = 1, message = "currentPage must be greater than 0") Integer currentPage,
                                  @RequestParam @NotNull(message = "size must not be null") @Min(value = 1, message = "size must be greater than 0") Integer size,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String capacityType,
                                  @RequestParam(required = false) String location) {
        return R.ok(roomService.pageRooms(currentPage, size, keyword, status, capacityType, location));
    }

    @GetMapping("/list")
    public R<List<RoomListItemVO>> listAlias() {
        return R.ok(roomService.listRooms(null, null, null));
    }

    @GetMapping("/options")
    public R<List<RoomOptionVO>> options() {
        return R.ok(roomMapper.selectOptions());
    }

    @GetMapping("/locations")
    public R<List<String>> locations() {
        return R.ok(roomMapper.selectLocations());
    }

    @GetMapping("/recommend")
    public R<List<RoomRecommendItemVO>> recommend(@RequestParam String start,
                                                  @RequestParam String end,
                                                  @RequestParam Integer attendees,
                                                  @RequestParam(required = false) String requiredDevices) {
        return R.ok(roomRecommendService.recommend(start, end, attendees, requiredDevices));
    }

    @PostMapping("/admin")
    public R<Long> adminCreate(@RequestBody RoomUpsertDTO dto) {
        return R.ok(roomService.adminCreate(dto));
    }

    @PutMapping("/admin")
    public R<Void> adminUpdate(@RequestBody RoomUpsertDTO dto) {
        roomService.adminUpdate(dto);
        return R.ok(null);
    }

    @DeleteMapping("/admin/{roomCode}")
    public R<Void> adminDelete(@PathVariable String roomCode) {
        roomService.adminDelete(roomCode);
        return R.ok(null);
    }

    @GetMapping("/admin/detail/{roomCode}")
    public R<RoomAdminDetailVO> adminDetail(@PathVariable String roomCode) {
        return R.ok(roomService.adminDetail(roomCode));
    }
}
