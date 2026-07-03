package com.group8.streetlight.config;

import com.group8.streetlight.ws.WsHandshakeInterceptor;
import com.group8.streetlight.ws.WsServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private WsServer wsServer;

    @Autowired
    private WsHandshakeInterceptor handshake;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(wsServer, "/ws")
                .addInterceptors(handshake)
                .setAllowedOrigins("*");
    }
}
