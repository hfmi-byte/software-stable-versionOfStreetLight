package com.group8.streetlight.mqtt;

import com.group8.streetlight.service.CommandSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** emqx 模式下用 MQTT 发命令；huawei 模式不会注入这个 Bean。 */
@Slf4j
@Component
@Conditional(MqttCondition.class)
@Order(20) // 排在 HuaweiCommandSender 之后；如同时存在，Spring 会按定义顺序注入 List
public class MqttCommandSender implements CommandSender {

    @Autowired private MqttSendClient sendClient;

    @Override
    public boolean send(String deviceId, String huaweiDeviceId, String action) {
        return sendClient.publishCommand(deviceId, action);
    }
}
