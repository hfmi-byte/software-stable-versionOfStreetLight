package com.group8.streetlight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * EMQX 模式下的 MQTT 配置（保留与 mqtt01-master 等价的能力）。
 * huawei 模式下整个 MQTT 客户端不会启动，这些字段也用不到。
 */
@Component
@ConfigurationProperties(prefix = "mqtt")
@Data
public class MqttProperties {
    private boolean enabled = false;
    private String hostUrl;
    private String clientIdPrefix = "group8-backend-";
    private String username;
    private String password;
    private int connectionTimeout = 10;
    private int keepAlive = 60;
    private boolean cleanSession = true;
    private boolean reconnect = true;
    private int qos = 1;

    /** topic 前缀（接口规范 §5 要求加 group8_streetlight） */
    private String topicPrefix = "group8_streetlight";
}
