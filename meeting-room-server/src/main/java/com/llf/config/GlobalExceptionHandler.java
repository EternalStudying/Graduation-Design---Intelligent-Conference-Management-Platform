package com.llf.config;

import com.llf.result.BizException;
import com.llf.result.ErrorCode;
import com.llf.result.R;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    // @RequestBody 校验失败
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    // 表单/Query 参数绑定失败
    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    // @Validated + @RequestParam 校验失败
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraint(ConstraintViolationException e) {
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    // JSON 解析失败
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), "请求体 JSON 格式错误");
    }

    // 兜底异常
    @ExceptionHandler(Exception.class)
    public R<Void> handleAny(Exception e) {
        // 生产建议记录日志，这里毕设可以先打印
        e.printStackTrace();
        return R.fail(ErrorCode.SYSTEM_ERROR);
    }
}