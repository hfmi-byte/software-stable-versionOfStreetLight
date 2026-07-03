package com.group8.streetlight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "huawei.iot")
@Data
public class HuaweiIotProperties {

    /** 应用侧 API endpoint，如 https://iotda.cn-north-4.myhuaweicloud.com */
    private String endpoint;

    /** 标准版/企业版实例 ID；基础版留空。非空时会作为 Instance-Id 请求头带上 */
    private String instanceId;

    /** 华为云 projectId */
    private String projectId;

    /** 产品 service_id（与设备侧保持一致，路灯例子里是 "Light"） */
    private String serviceId = "Light";

    /** 命令名（路灯例子里是 "Light_Control_Led"） */
    private String commandName = "Light_Control_Led";

    /** 数据转发推送 payload 里的 service_id 字段（一般同 serviceId） */
    private String reportServiceId = "Light";

    /** AK/SK 认证方式 (优先于 IAM Token) */
    private String accessKey;
    private String secretKey;

    private Iam iam = new Iam();

    @Data
    public static class Iam {
        private String endpoint = "https://iam.myhuaweicloud.com";
        private String domain;
        private String username;
        private String password;
        private String projectName = "cn-north-4";
    }
}
