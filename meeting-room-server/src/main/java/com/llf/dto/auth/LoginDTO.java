package com.llf.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank(message = "username must not be blank")
    private String username;

    @NotBlank(message = "password must not be blank")
    private String password;

    @NotBlank(message = "code must not be blank")
    private String code;

    @NotBlank(message = "captchaId must not be blank")
    private String captchaId;
}

