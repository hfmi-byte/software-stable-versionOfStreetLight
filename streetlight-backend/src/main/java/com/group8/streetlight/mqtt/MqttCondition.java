package com.group8.streetlight.mqtt;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** mqtt.enabled=true 且 streetlight.mode 不是 huawei 时启用。 */
public class MqttCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        boolean mqttEnabled = Boolean.parseBoolean(env.getProperty("mqtt.enabled", "false"));
        String mode = env.getProperty("streetlight.mode", "huawei");
        return mqttEnabled && !"huawei".equalsIgnoreCase(mode);
    }
}
