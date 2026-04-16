package com.llf.vo.auth;

import lombok.Data;

@Data
public class CaptchaVO {
    private String captchaId;
    private String imageBase64;
}

