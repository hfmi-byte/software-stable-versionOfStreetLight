package com.group8.streetlight.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 接口规范 §1.4 错误体：{ code, message } */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {
    private String code;
    private String message;
}
