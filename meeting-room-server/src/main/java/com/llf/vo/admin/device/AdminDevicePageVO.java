package com.llf.vo.admin.device;

import lombok.Data;

import java.util.List;

@Data
public class AdminDevicePageVO {
    private List<AdminDeviceVO> list;
    private Long total;
    private AdminDeviceStatsVO stats;
}

