package com.cjh.mqtt.controller;

import com.cjh.mqtt.util.MqttAcceptCallback;
import com.cjh.mqtt.util.MqttSendClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MqttController {

    private static final Logger logger = LoggerFactory.getLogger(MqttController.class);

    @Autowired
    private MqttSendClient MqttSendClient;

    @GetMapping(value = "/publishTopic")
    public Object publishTopic(String sendMessage) {
       // System.out.println("message:"+sendMessage);
        logger.info(sendMessage);
        sendMessage= "001";
//        MqttSendClient.publish(false,"client:report:2",sendMessage);
        //发送消息
        MqttSendClient.publish(false ,"abc001",sendMessage);
        return null;
    }


    @GetMapping(value = "/on")
    public Object on() {
        MqttSendClient.publish(false ,"abc001","1");
        return null;
    }

    @GetMapping(value = "/off")
    public Object off() {
        MqttSendClient.publish(false ,"abc001","0");
        return null;
    }

}

