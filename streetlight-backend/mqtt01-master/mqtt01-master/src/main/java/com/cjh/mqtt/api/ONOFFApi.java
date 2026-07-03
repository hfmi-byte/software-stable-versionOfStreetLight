package com.cjh.mqtt.api;


import com.cjh.mqtt.util.MqttSendClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@CrossOrigin
@RestController
public class ONOFFApi {

    @Autowired
    private MqttSendClient MqttSendClient;

    @PostMapping("/on")
    public Map on(){
        MqttSendClient.publish(false ,"abc111","1");
        return  null;

    }

    @PostMapping("/off")
    public Map off(){
        MqttSendClient.publish(false ,"abc111","2");
        return  null;

    }
}
