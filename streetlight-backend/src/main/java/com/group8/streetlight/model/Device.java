package com.group8.streetlight.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 对前端的设备视图（接口规范 §2 Device）。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Device {
    private String id;
    private String name;
    private String location;
    private Boolean online;
    private Boolean lamp;
    private Integer light;
    private String lastSeen;     // ISO 8601
    private Integer threshold;
    private Boolean autoMode;
    /** 仅 admin 接口用得到，普通响应可以为 null */
    private String huaweiDeviceId;
}
