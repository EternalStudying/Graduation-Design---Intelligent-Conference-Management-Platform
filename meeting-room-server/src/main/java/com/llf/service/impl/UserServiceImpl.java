package com.llf.service.impl;

import com.llf.mapper.SysUserMapper;
import com.llf.service.UserService;
import com.llf.vo.user.UserOptionVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    @Resource
    private SysUserMapper sysUserMapper;

    @Override
    public List<UserOptionVO> searchActiveUsersByDisplayName(String keyword, Integer limit, Long excludeUserId) {
        String trimmedKeyword = keyword == null ? null : keyword.trim();
        if (trimmedKeyword == null || trimmedKeyword.isBlank()) {
            return List.of();
        }
        return sysUserMapper.searchActiveUsersByDisplayName(trimmedKeyword, resolveLimit(limit), excludeUserId)
                .stream()
                .map(this::toUserOptionVO)
                .toList();
    }

    @Override
    public List<UserOptionVO> listActiveUsersByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> deduplicatedIds = userIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (deduplicatedIds.isEmpty()) {
            return List.of();
        }
        Map<Long, UserOptionVO> optionMap = new LinkedHashMap<>();
        sysUserMapper.selectActiveUsersByIds(deduplicatedIds).forEach(row -> optionMap.put(row.getId(), toUserOptionVO(row)));
        return optionMap.values().stream().toList();
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private UserOptionVO toUserOptionVO(SysUserMapper.UserSearchRow row) {
        UserOptionVO vo = new UserOptionVO();
        vo.setId(row.getId());
        vo.setUsername(row.getUsername());
        vo.setNickname(row.getDisplayName());
        vo.setDisplayName(row.getDisplayName() + "（" + row.getUsername() + "）");
        return vo;
    }
}
