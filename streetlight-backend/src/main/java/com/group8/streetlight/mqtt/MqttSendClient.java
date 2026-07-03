package com.group8.streetlight.mqtt;

import cn.hutool.json.JSONObject;
import com.group8.streetlight.config.MqttProperties;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
@Conditional(MqttCondition.class)
public class MqttSendClient {

    @Autowired private MqttProperties props;

    private volatile MqttClient client;

    @PostConstruct
    public void start() {
        try {
            String clientId = props.getClientIdPrefix() + "send-" + System.currentTimeMillis();
            client = new MqttClient(props.getHostUrl(), clientId, new MemoryPersistence());
            MqttConnectOptions opt = new MqttConnectOptions();
            if (props.getUsername() != null && !props.getUsername().isEmpty()) {
                opt.setUserName(props.getUsername());
                opt.setPassword(props.getPassword() == null ? new char[0] : props.getPassword().toCharArray());
            }
            opt.setConnectionTimeout(props.getConnectionTimeout());
            opt.setKeepAliveInterval(props.getKeepAlive());
            opt.setCleanSession(true);
            opt.setAutomaticReconnect(props.isReconnect());
            client.connect(opt);
            log.info("EMQX 发送客户端连接成功 clientId={}", clientId);
        } catch (Exception e) {
            log.error("EMQX 发送客户端连接失败", e);
        }
    }

    /** 发布命令到 group8_streetlight/<id>/cmd。 */
    public boolean publishCommand(String deviceId, String action) {
        if (client == null || !client.isConnected()) {
            log.warn("EMQX 发送客户端未连接");
            return false;
        }
        String topic = props.getTopicPrefix() + "/" + deviceId + "/cmd";
        String payload = new JSONObject().set("action", action).toString();
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes());
            msg.setQos(props.getQos());
            client.publish(topic, msg);
            log.info("[MQTT-pub] {} -> {}", topic, payload);
            return true;
        } catch (Exception e) {
            log.error("发布失败 topic={}", topic, e);
            return false;
        }
    }
}
