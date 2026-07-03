package com.group8.streetlight.ws;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.group8.streetlight.util.Times;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * WebSocket 推送端点（接口规范 §4）。
 * 所有面向前端的实时事件统一从这里发出。
 */
@Slf4j
@Component
public class WsServer extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService wsExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "streetlight-ws-broadcast");
            t.setDaemon(true);
            return t;
        }
    });

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WS 连接建立 sid={} role={} 当前连接数={}",
                session.getId(), session.getAttributes().get("role"), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WS 连接断开 sid={} status={} 剩余={}", session.getId(), status, sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 前端预留的 subscribe 扩展位（§4.2）。当前不做处理。
        log.debug("WS recv: {}", message.getPayload());
    }

    /** 广播事件给所有在线浏览器。 */
    public void broadcast(String type, Object payload) {
        JSONObject env = new JSONObject().set("type", type).set("payload", payload);
        String text = JSONUtil.toJsonStr(env);
        wsExecutor.submit(new Runnable() {
            @Override
            public void run() {
                TextMessage msg = new TextMessage(text);
                for (WebSocketSession s : sessions.values()) {
                    try {
                        if (s.isOpen()) s.sendMessage(msg);
                    } catch (IOException e) {
                        log.warn("WS 推送失败 sid={}: {}", s.getId(), e.getMessage());
                    }
                }
            }
        });
    }

    /** 服务端心跳（接口规范 §4.2） */
    @Scheduled(fixedRate = 30000L)
    public void serverHeartbeat() {
        if (sessions.isEmpty()) return;
        broadcast("heartbeat", new JSONObject().set("ts", Times.nowIso()));
    }
}
