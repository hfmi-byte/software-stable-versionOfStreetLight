package com.group8.streetlight.mqtt;

import com.group8.streetlight.config.MqttProperties;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
@Conditional(MqttCondition.class)
public class MqttAcceptClient {

    @Autowired private MqttProperties props;
    @Autowired private MqttAcceptCallback callback;

    private volatile MqttClient client;

    @PostConstruct
    public void start() {
        try {
            String clientId = props.getClientIdPrefix() + "recv-" + System.currentTimeMillis();
            client = new MqttClient(props.getHostUrl(), clientId, new MemoryPersistence());
            MqttConnectOptions opt = new MqttConnectOptions();
            if (props.getUsername() != null && !props.getUsername().isEmpty()) {
                opt.setUserName(props.getUsername());
                opt.setPassword(props.getPassword() == null ? new char[0] : props.getPassword().toCharArray());
            }
            opt.setConnectionTimeout(props.getConnectionTimeout());
            opt.setKeepAliveInterval(props.getKeepAlive());
            opt.setCleanSession(props.isCleanSession());
            opt.setAutomaticReconnect(props.isReconnect());
            client.setCallback(callback);
            client.connect(opt);
            log.info("EMQX 接收客户端连接成功 clientId={}", clientId);
        } catch (Exception e) {
            log.error("EMQX 接收客户端连接失败", e);
        }
    }

    public void subscribe(String topic, int qos) {
        if (client == null || !client.isConnected()) return;
        try {
            client.subscribe(topic, qos);
            log.info("EMQX 订阅 {} qos={}", topic, qos);
        } catch (MqttException e) {
            log.error("订阅失败 topic={}", topic, e);
        }
    }
}
