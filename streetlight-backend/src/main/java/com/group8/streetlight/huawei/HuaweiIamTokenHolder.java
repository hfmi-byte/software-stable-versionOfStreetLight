package com.group8.streetlight.huawei;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.group8.streetlight.config.HuaweiIotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 通过 IAM password 鉴权拿 X-Subject-Token，缓存复用。
 * Token 默认 24h 有效，提前 30 分钟刷新。
 *
 * 文档参考：华为云 IAM "获取用户Token" 接口
 *   POST {iam}/v3/auth/tokens
 */
@Slf4j
@Component
public class HuaweiIamTokenHolder {

    @Autowired
    private HuaweiIotProperties props;

    private volatile String token;
    private volatile long expireAt;        // ms epoch
    private final ReentrantLock lock = new ReentrantLock();

    @PostConstruct
    public void init() {
        if (isConfigured()) {
            try { 
                refresh(); 
                log.info("IAM Token 模式: 认证成功");
            } catch (Exception e) {
                log.warn("初始化 IAM token 失败（不影响启动）：{}", e.getMessage());
            }
        } else {
            // 检查是否配置了 AK/SK
            if (props.getAccessKey() != null && !props.getAccessKey().isEmpty()) {
                log.info("AK/SK 模式: 已启用,无需 IAM Token");
            } else {
                log.warn("华为 IAM 未配置或字段为空，将无法调用应用侧 API (请配置 IAM 或 AK/SK)");
            }
        }
    }

    public boolean isConfigured() {
        HuaweiIotProperties.Iam iam = props.getIam();
        return iam != null
                && StrUtil.isNotBlank(iam.getDomain())
                && StrUtil.isNotBlank(iam.getUsername())
                && StrUtil.isNotBlank(iam.getPassword())
                && !iam.getDomain().startsWith("华为云")  // 占位符没改
                && !iam.getUsername().startsWith("IAM");
    }

    public String getToken() {
        if (!isConfigured()) return null;
        if (token != null && System.currentTimeMillis() < expireAt - 30 * 60 * 1000L) {
            return token;
        }
        lock.lock();
        try {
            if (token != null && System.currentTimeMillis() < expireAt - 30 * 60 * 1000L) return token;
            refresh();
            return token;
        } finally {
            lock.unlock();
        }
    }

    private void refresh() {
        HuaweiIotProperties.Iam iam = props.getIam();
        String body = buildBody(iam);
        String url = iam.getEndpoint() + "/v3/auth/tokens?nocatalog=true";
        log.info("向 {} 申请 IAM token (domain={}, user={})", url, iam.getDomain(), iam.getUsername());
        try (HttpResponse resp = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(body)
                .timeout(10_000)
                .execute()) {
            if (resp.getStatus() / 100 != 2) {
                throw new RuntimeException("IAM 鉴权失败 status=" + resp.getStatus() + " body=" + resp.body());
            }
            String tok = resp.header("X-Subject-Token");
            if (StrUtil.isBlank(tok)) throw new RuntimeException("IAM 响应里没有 X-Subject-Token");
            this.token = tok;
            // 解析 body 里的 expires_at
            JSONObject json = JSONUtil.parseObj(resp.body());
            String expiresAt = json.getByPath("token.expires_at", String.class);
            if (StrUtil.isNotBlank(expiresAt)) {
                try {
                    this.expireAt = java.time.OffsetDateTime.parse(expiresAt).toInstant().toEpochMilli();
                } catch (Exception ex) {
                    this.expireAt = System.currentTimeMillis() + 22 * 3600 * 1000L;
                }
            } else {
                this.expireAt = System.currentTimeMillis() + 22 * 3600 * 1000L;
            }
            log.info("IAM token 刷新成功，过期时间 {}", new java.util.Date(expireAt));
        }
    }

    private String buildBody(HuaweiIotProperties.Iam iam) {
        return "{" +
                "\"auth\":{" +
                "\"identity\":{" +
                "\"methods\":[\"password\"]," +
                "\"password\":{\"user\":{" +
                "\"name\":\"" + iam.getUsername() + "\"," +
                "\"password\":\"" + iam.getPassword() + "\"," +
                "\"domain\":{\"name\":\"" + iam.getDomain() + "\"}" +
                "}}}," +
                "\"scope\":{\"project\":{\"name\":\"" + iam.getProjectName() + "\"}}" +
                "}}";
    }
}
