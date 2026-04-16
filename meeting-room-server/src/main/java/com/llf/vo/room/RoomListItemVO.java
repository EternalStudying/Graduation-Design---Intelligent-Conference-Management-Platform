package com.llf.vo.room;

import lombok.Data;
import java.util.List;

@Data
public class RoomListItemVO {
    private Long id;              // 鏁版嵁搴撲富閿?
    private String roomCode;      // R101 / A101
    private String name;          // 涓€鍙蜂細璁
    private String location;      // A搴?1F
    private Integer capacity;     // 瀹归噺
    private String status;        // AVAILABLE / MAINTENANCE
    private List<String> devices; // 璁惧鍚嶇О鍒楄〃锛氭姇褰变华/鐧芥澘...
}
