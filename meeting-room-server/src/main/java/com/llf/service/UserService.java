package com.llf.service;

import com.llf.vo.user.UserOptionVO;

import java.util.List;

public interface UserService {
    List<UserOptionVO> searchActiveUsersByDisplayName(String keyword, Integer limit, Long excludeUserId);

    List<UserOptionVO> listActiveUsersByIds(List<Long> userIds);
}
