package com.group8.streetlight.ws;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.group8.streetlight.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtil jwt;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest req = ((ServletServerHttpRequest) request).getServletRequest();
            String token = req.getParameter("token");
            if (StrUtil.isBlank(token)) {
                log.warn("WS 握手拒绝：缺少 token");
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
            JSONObject payload = jwt.verify(token);
            if (payload == null) {
                log.warn("WS 握手拒绝：token 无效");
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put("username", payload.getStr("sub"));
            attributes.put("role", payload.getStr("role", "municipal"));
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
