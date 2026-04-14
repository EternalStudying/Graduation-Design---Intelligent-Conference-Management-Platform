package com.llf.result;

import lombok.Data;

@Data
public class R<T> {
    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(ErrorCode.OK.getCode());
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> R<T> fail(ErrorCode ec) {
        R<T> r = new R<>();
        r.setCode(ec.getCode());
        r.setMessage(ec.getMsg());
        r.setData(null);
        return r;
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(null);
        return r;
    }
}
