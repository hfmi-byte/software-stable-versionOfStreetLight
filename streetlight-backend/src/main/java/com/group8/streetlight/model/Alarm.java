package com.group8.streetlight.model;

import lombok.Data;

@Data
public class Alarm {
    private String id;
    private String time;     // ISO 8601
    private String device;
    private String type;     // 设备离线 | 自动开灯 | 自动关灯 | 通信恢复 | 光照异常
    private String content;
    private String level;    // 低 | 中 | 高
    private String status;   // 未处理 | 已处理
}
