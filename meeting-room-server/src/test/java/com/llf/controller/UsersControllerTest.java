package com.llf.controller;

import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.config.GlobalExceptionHandler;
import com.llf.service.AuthService;
import com.llf.service.UserService;
import com.llf.vo.user.UserOptionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsersControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final UserService userService = mock(UserService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthContext.set(currentUser(1L));
        UsersController controller = new UsersController();
        inject(controller, "authService", authService);
        inject(controller, "userService", userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void search_shouldReturnMatchedUsersByDisplayNameAndExcludeCurrentUser() throws Exception {
        when(userService.searchActiveUsersByDisplayName("张", 5, 1L)).thenReturn(List.of(
                userOption(101L, "zhangsan", "张三"),
                userOption(102L, "zhangxiaomei", "张晓梅")
        ));

        mockMvc.perform(get("/api/v1/users/search")
                        .param("keyword", "张")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].nickname").value("张三"))
                .andExpect(jsonPath("$.data[0].displayName").value("张三（zhangsan）"))
                .andExpect(jsonPath("$.data[1].displayName").value("张晓梅（zhangxiaomei）"));

        verify(userService).searchActiveUsersByDisplayName(eq("张"), eq(5), eq(1L));
    }

    @Test
    void search_shouldReturnEmptyArrayWhenKeywordBlank() throws Exception {
        when(userService.searchActiveUsersByDisplayName(" ", null, 1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/search")
                        .param("keyword", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(userService).searchActiveUsersByDisplayName(eq(" "), isNull(), eq(1L));
    }

    private UserOptionVO userOption(Long id, String username, String nickname) {
        UserOptionVO vo = new UserOptionVO();
        vo.setId(id);
        vo.setUsername(username);
        vo.setNickname(nickname);
        vo.setDisplayName(nickname + "（" + username + "）");
        return vo;
    }

    private AuthUser currentUser(Long id) {
        AuthUser user = new AuthUser();
        user.setId(id);
        user.setUsername("tester");
        user.setDisplayName("测试用户");
        user.setRole("USER");
        return user;
    }

    private void inject(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
