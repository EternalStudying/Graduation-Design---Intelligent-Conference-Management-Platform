package com.llf.controller;

import com.llf.dto.admin.room.AdminRoomDevicesDTO;
import com.llf.dto.admin.room.AdminRoomStatusDTO;
import com.llf.dto.admin.room.RoomUpsertDTO;
import com.llf.result.R;
import com.llf.service.RoomService;
import com.llf.vo.room.RoomPageDataVO;
import com.llf.vo.room.RoomPageItemVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/admin/rooms")
public class AdminRoomController {

    @Resource
    private RoomService roomService;

    @GetMapping
    public R<RoomPageDataVO> page(@RequestParam @NotNull(message = "currentPage must not be null") @Min(value = 1, message = "currentPage must be greater than 0") Integer currentPage,
                                  @RequestParam @NotNull(message = "size must not be null") @Min(value = 1, message = "size must be greater than 0") Integer size,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String capacityType,
                                  @RequestParam(required = false) String location) {
        return R.ok(roomService.adminPage(currentPage, size, keyword, status, capacityType, location));
    }

    @GetMapping("/{id}")
    public R<RoomPageItemVO> detail(@PathVariable Long id) {
        return R.ok(roomService.adminDetailById(id));
    }

    @PostMapping
    public R<Long> create(@Valid @RequestBody RoomUpsertDTO dto) {
        return R.ok(roomService.adminCreateV2(dto));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody RoomUpsertDTO dto) {
        roomService.adminUpdateV2(id, dto);
        return R.ok(null);
    }

    @PatchMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody AdminRoomStatusDTO dto) {
        roomService.adminUpdateStatus(id, dto);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        roomService.adminDeleteById(id);
        return R.ok(null);
    }

    @PutMapping("/{id}/devices")
    public R<Void> updateDevices(@PathVariable Long id, @Valid @RequestBody AdminRoomDevicesDTO dto) {
        roomService.adminUpdateDevices(id, dto);
        return R.ok(null);
    }
}
