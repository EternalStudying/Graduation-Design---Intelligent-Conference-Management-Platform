package com.llf.result;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(ErrorCode ec) {
        super(ec.getMsg());
        this.code = ec.getCode();
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public static BizException of(ErrorCode ec) {
        return new BizException(ec);
    }
}