package com.group8.streetlight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "streetlight")
@Data
public class StreetlightProperties {

    /** huawei | emqx | both */
    private String mode = "huawei";

    /** 心跳超时（秒）：超过未上报视为离线 */
    private int offlineTimeoutSec = 60;

    /** 离线检测扫描周期（毫秒） */
    private long offlineScanIntervalMs = 30000L;

    /** 自动联动冷却时间（秒） */
    private int autoCommandCooldownSec = 30;

    /** 自动关灯阈值滞回值（lux） */
    private int autoOffHysteresisLux = 30;

    /** 历史光照采样间隔（秒） */
    private int historySampleIntervalSec = 3;

    private Jwt jwt = new Jwt();

    private List<DefaultAccount> defaultAccounts = new ArrayList<>();

    public boolean isHuaweiEnabled() {
        return "huawei".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
    }

    public boolean isEmqxEnabled() {
        return "emqx".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
    }

    @Data
    public static class Jwt {
        private String secret = "change-me";
        private int expireHours = 24;
    }

    @Data
    public static class DefaultAccount {
        private String username;
        private String password;
        private String role;
    }
}
