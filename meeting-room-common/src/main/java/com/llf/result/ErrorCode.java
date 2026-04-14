package com.llf.result;

import lombok.Getter;

@Getter
public enum ErrorCode {
    OK(0, "success"),
    PARAM_ERROR(400, "invalid request parameters"),
    NOT_FOUND(404, "resource not found"),
    CAPTCHA_INVALID(1001, "captcha invalid"),
    CAPTCHA_EXPIRED(1002, "captcha expired"),
    BIZ_ERROR(500, "business error"),
    SYSTEM_ERROR(5000, "system error");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
