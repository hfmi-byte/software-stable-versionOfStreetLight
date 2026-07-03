package com.group8.streetlight.service;

/**
 * 对设备下发开关灯命令的统一入口。
 * huawei 模式注入 HuaweiCommandSender；emqx 模式注入 MqttCommandSender；both 模式两个都跑。
 */
public interface CommandSender {
    /**
     * @param deviceId        前端用的设备 ID（如 light001）
     * @param huaweiDeviceId  绑定的华为云设备 ID，可为空（emqx 模式忽略）
     * @param action          "turn_on" 或 "turn_off"
     * @return 是否送达
     */
    boolean send(String deviceId, String huaweiDeviceId, String action);
}
