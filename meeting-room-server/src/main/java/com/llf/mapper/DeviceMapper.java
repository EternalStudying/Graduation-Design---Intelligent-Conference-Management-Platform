package com.llf.mapper;

import com.llf.vo.DeviceEnabledVO;
import com.llf.vo.DeviceOptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DeviceMapper {

    @Select("SELECT id, name FROM device WHERE status='ENABLED' ORDER BY id")
    List<DeviceOptionVO> selectEnabledOptions();

    @Select("SELECT id, name, total FROM device WHERE status='ENABLED' ORDER BY id")
    List<DeviceEnabledVO> selectEnabledList();
}