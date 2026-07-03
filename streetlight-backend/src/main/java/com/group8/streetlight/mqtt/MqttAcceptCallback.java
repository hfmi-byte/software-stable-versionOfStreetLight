package com.group8.streetlight.mqtt;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.group8.streetlight.config.MqttProperties;
import com.group8.streetlight.service.DeviceService;
import com.group8.streetlight.util.Times;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EMQX 模式 MQTT 回调：
 *   group8_streetlight/<id>/data       上报光照 + 灯
 *   group8_streetlight/<id>/heartbeat  心跳
 *   group8_streetlight/<id>/feedback   命令执行反馈
 *
 * 与原 mqtt01-master MqttAcceptCallback（解析 toilet_state / light_state）相比，已重写为本项目 payload。
 */
@Slf4j
@Component
@Conditional(MqttCondition.class)
public class MqttAcceptCallback implements MqttCallbackExtended {

    private static final Pattern TOPIC_PATTERN = Pattern.compile("^([^/]+)/([^/]+)/(data|heartbeat|feedback)$");

    @Autowired private MqttAcceptClient mqttAcceptClient;
    @Autowired private MqttProperties mqttProps;
    @Autowired private DeviceService deviceService;

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("EMQX 连接断开：{}", cause == null ? "" : cause.getMessage());
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("EMQX 连接成功 reconnect={} uri={}", reconnect, serverURI);
        String prefix = mqttProps.getTopicPrefix();
        mqttAcceptClient.subscribe(prefix + "/+/data", mqttProps.getQos());
        mqttAcceptClient.subscribe(prefix + "/+/heartbeat", mqttProps.getQos());
        mqttAcceptClient.subscribe(prefix + "/+/feedback", mqttProps.getQos());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        log.debug("[MQTT-recv] topic={} payload={}", topic, payload);
        Matcher m = TOPIC_PATTERN.matcher(topic);
        if (!m.matches()) {
            log.debug("忽略不符合约定的 topic: {}", topic);
            return;
        }
        String prefix = m.group(1);
        String deviceId = m.group(2);
        String kind = m.group(3);
        if (!prefix.equals(mqttProps.getTopicPrefix())) return;

        try {
            JSONObject json = StrUtil.isBlank(payload) ? new JSONObject() : JSONUtil.parseObj(payload);
            Date ts = Times.fromIso(json.getStr("ts"));
            if (ts == null) ts = new Date();
            switch (kind) {
                case "data": {
                    Integer light = json.getInt("light");
                    Boolean lamp = json.getBool("lamp");
                    deviceService.onTelemetry(deviceId, light, lamp, ts);
                    break;
                }
                case "heartbeat": {
                    deviceService.onTelemetry(deviceId, null, null, ts);
                    break;
                }
                case "feedback": {
                    String action = json.getStr("action", "");
                    Boolean ok = json.getBool("ok");
                    deviceService.onCommandFeedback(deviceId, Boolean.TRUE.equals(ok), action);
                    break;
                }
                default: break;
            }
        } catch (Exception e) {
            log.error("解析 MQTT 消息失败 topic={}", topic, e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        if (token != null && token.getTopics() != null) {
            for (String t : token.getTopics()) log.debug("MQTT 发送完成: {}", t);
        }
    }
}
