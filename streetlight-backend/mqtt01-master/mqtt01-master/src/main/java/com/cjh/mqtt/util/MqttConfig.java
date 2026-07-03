package com.cjh.mqtt.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class MqttConfig {

    @Autowired
    private MqttAcceptClient mqttAcceptClient;




//    @Autowired
//    private MqttAcceptClient1 mqttAcceptClient1;

    /**
     * 订阅mqtt
     *
     * @return
     */
    @Conditional(MqttCondition.class)
    @Bean
    public MqttAcceptClient getMqttPushClient() {
        //对象加载的时候，调用了连接的方法
        mqttAcceptClient.connect();
        return mqttAcceptClient;
    }

//    @Conditional(MqttCondition.class)
//    @Bean
//    public MqttAcceptClient1 getMqttPushClient1() {
//        mqttAcceptClient1.connect();
//        return mqttAcceptClient1;
//    }




}

