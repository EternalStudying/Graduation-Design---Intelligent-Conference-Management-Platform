package com.llf.vo.admin.device;

import lombok.Data;

@Data
public class AdminDeviceStatsVO {
    private Integer totalCount;
    private Integer enabledCount;
    private Integer disabledCount;
    private Integer warningCount;
}

