package com.llf.service;

import com.llf.vo.auth.CaptchaVO;

public interface CaptchaService {

    CaptchaVO createCaptcha();

    void verifyCaptcha(String captchaId, String code);
}
