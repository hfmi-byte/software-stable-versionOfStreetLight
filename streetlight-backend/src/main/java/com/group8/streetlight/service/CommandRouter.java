package com.group8.streetlight.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 命令分发路由，持有所有 CommandSender 实现（HuaweiCommandSender / MqttCommandSender）。
 * 标记 @Primary，确保 DeviceService 自动注入此 bean 而不是某个具体实现。
 * 依次尝试列表里的每个 sender；任一返回 true 即视为送达。
 */
@Slf4j
@Component
@Primary
public class CommandRouter implements CommandSender {

    private final List<CommandSender> delegates;

    /** required=false 防止 0 个实现时启动失败 */
    @Autowired(required = false)
    public CommandRouter(List<CommandSender> all) {
        // 过滤掉自己，避免循环引用
        this.delegates = (all == null)
                ? Collections.emptyList()
                : all.stream()
                        .filter(s -> !(s instanceof CommandRouter))
                        .collect(Collectors.toList());
        log.info("CommandRouter 持有 {} 个 sender: {}",
                delegates.size(),
                delegates.stream().map(s -> s.getClass().getSimpleName()).collect(Collectors.toList()));
    }

    @Override
    public boolean send(String deviceId, String huaweiDeviceId, String action) {
        if (delegates.isEmpty()) {
            log.warn("无可用 CommandSender，请检查 streetlight.mode / 华为云 IAM 配置");
            return false;
        }
        for (CommandSender s : delegates) {
            try {
                boolean ok = s.send(deviceId, huaweiDeviceId, action);
                if (ok) return true;
            } catch (Exception e) {
                log.warn("{}.send 异常: {}", s.getClass().getSimpleName(), e.getMessage());
            }
        }
        log.warn("所有 CommandSender 均失败，deviceId={} action={}", deviceId, action);
        return false;
    }
}
