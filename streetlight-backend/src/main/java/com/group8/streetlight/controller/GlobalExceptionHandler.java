package com.group8.streetlight.controller;

import com.group8.streetlight.model.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 客户端（华为云推送 / ngrok / 浏览器）在响应写完前主动断开连接。
     * 此时连接已死，不能也不需要再写响应体，只记一行 debug，避免刷屏。
     */
    @ExceptionHandler(ClientAbortException.class)
    public void clientAbort(ClientAbortException e) {
        log.debug("客户端提前断开连接: {}", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> any(Exception e) {
        log.error("未捕获异常", e);
        return ResponseEntity.status(500).body(new ApiError("INTERNAL_ERROR", e.getMessage()));
    }
}
