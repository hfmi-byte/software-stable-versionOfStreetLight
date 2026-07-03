package com.group8.streetlight.huawei;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.group8.streetlight.config.HuaweiIotProperties;
import com.group8.streetlight.dao.DeviceDao;
import com.group8.streetlight.model.Device;
import com.group8.streetlight.service.DeviceService;
import com.group8.streetlight.util.Times;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 接收华为云 IoTDA "数据转发 → HTTP/HTTPS 推送" 的回调。
 *
 * 在华为云控制台配置：
 *   规则 -> 数据转发 -> 创建规则 -> 转发数据类型选 "设备属性" / "设备消息" / "设备状态"
 *   转发目标选 HTTP 推送，URL 填：
 *       http://<本机外网>:9091/api/huawei/push
 *
 * 推送 body 形如：
 * {
 *   "resource": "device.property",
 *   "event": "report",
 *   "event_time": "20260630T201500Z",
 *   "notify_data": {
 *     "header": { "device_id": "xxx_light001", "product_id": "...", "gateway_id": "..." },
 *     "body": {
 *       "services": [{
 *         "service_id": "Light",
 *         "properties": { "Luminance": 268, "LightStatus": "ON" },
 *         "event_time": "20260630T201500Z"
 *       }]
 *     }
 *   }
 * }
 *
 * 对于 device.status 资源则携带 status = ONLINE / OFFLINE。
 */
@Slf4j
@RestController
@RequestMapping("/api/huawei")
public class HuaweiPushController {

    @Autowired private DeviceService deviceService;
    @Autowired private DeviceDao deviceDao;
    @Autowired private HuaweiIotProperties props;

    // 异步处理线程池：push 端点收到请求后立即返回 200，处理逻辑在此池内执行
    // 防止 IoTDA 因后端响应慢而超时中止连接，进而触发退避机制
    private final ExecutorService pushProcessor = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private int idx = 0;
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "streetlight-push-proc-" + (++idx));
            t.setDaemon(true);
            return t;
        }
    });

    private final AtomicLong pushCount = new AtomicLong();
    private final Map<String, String> deviceIdByHuaweiId = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPushAtByDevice = new ConcurrentHashMap<>();
    private final Map<String, String> lastResourceByDevice = new ConcurrentHashMap<>();
    private volatile long lastPushAt;
    private volatile String lastResource;
    private volatile String lastEvent;
    private volatile String lastHuaweiDeviceId;
    private volatile String lastDeviceId;
    private volatile long lastHandleCostMs;

    @PostMapping("/push")
    public Object push(@RequestBody String raw) {
        // 立即返回 200，防止 IoTDA 因等待超时而中止连接并触发退避机制。
        // 实际处理（DB 写入、自动联动）在后台线程异步执行。
        pushProcessor.submit(() -> processPush(raw));
        return ok();
    }

    private void processPush(String raw) {
        long startedAt = System.currentTimeMillis();
        log.trace("处理华为云推送: {}", raw);
        try {
            JSONObject root = JSONUtil.parseObj(raw);
            String resource = root.getStr("resource", "");
            String event = root.getStr("event", "");
            JSONObject notify = root.getJSONObject("notify_data");
            if (notify == null) return;

            JSONObject header = notify.getJSONObject("header");
            JSONObject body = notify.getJSONObject("body");
            if (header == null || body == null) return;
            String huaweiDeviceId = header.getStr("device_id");
            if (StrUtil.isBlank(huaweiDeviceId)) return;

            String deviceId = resolveDeviceId(huaweiDeviceId);
            if (deviceId == null) {
                log.warn("收到未绑定设备的推送 huawei_device_id={}，请在 devices 表绑定后再试", huaweiDeviceId);
                return;
            }
            recordPush(resource, event, huaweiDeviceId, deviceId);

            Date eventTime = parseTime(root.getStr("event_time"));
            if (eventTime == null) eventTime = new Date();

            if ("device.property".equalsIgnoreCase(resource) && "report".equalsIgnoreCase(event)) {
                handleProperty(deviceId, body, eventTime);
            } else if ("device.message".equalsIgnoreCase(resource)) {
                handleMessage(deviceId, body, eventTime);
            } else if ("device.status".equalsIgnoreCase(resource)) {
                handleStatus(deviceId, body);
            } else if ("device.command.status".equalsIgnoreCase(resource)) {
                // 命令执行结果（同步命令时不需要走这里，异步命令才会）
                handleCommandFeedback(deviceId, body);
            } else {
                log.debug("未处理的推送类型 resource={} event={}", resource, event);
            }
        } catch (Exception e) {
            log.error("解析华为推送失败", e);
        } finally {
            lastHandleCostMs = System.currentTimeMillis() - startedAt;
        }
    }

    private String resolveDeviceId(String huaweiDeviceId) throws SQLException {
        String cached = deviceIdByHuaweiId.get(huaweiDeviceId);
        if (StrUtil.isNotBlank(cached)) {
            return cached;
        }
        Device d = deviceDao.findByHuaweiDeviceId(huaweiDeviceId);
        if (d == null) {
            return null;
        }
        deviceIdByHuaweiId.put(huaweiDeviceId, d.getId());
        return d.getId();
    }

    private void handleProperty(String deviceId, JSONObject body, Date eventTime) {
        JSONArray services = body.getJSONArray("services");
        if (services == null) return;
        Integer light = null;
        Boolean lamp = null;
        for (int i = 0; i < services.size(); i++) {
            JSONObject svc = services.getJSONObject(i);
            if (!props.getReportServiceId().equalsIgnoreCase(svc.getStr("service_id"))) continue;
            JSONObject p = svc.getJSONObject("properties");
            if (p == null) continue;
            if (p.containsKey("Luminance")) {
                Object v = p.get("Luminance");
                if (v instanceof Number) light = ((Number) v).intValue();
                else if (v != null) try { light = Integer.parseInt(v.toString()); } catch (Exception ignore) {}
            }
            if (p.containsKey("LightStatus")) {
                String s = p.getStr("LightStatus", "");
                if ("ON".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s) || "1".equals(s)) lamp = true;
                else if ("OFF".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s) || "0".equals(s)) lamp = false;
            }
        }
        deviceService.onTelemetry(deviceId, light, lamp, eventTime);
    }

    /** 设备消息上报（非属性）：payload 自由 JSON，按需解析。这里兼容 {light, lamp, ts} 这种简化结构。 */
    private void handleMessage(String deviceId, JSONObject body, Date eventTime) {
        String contentStr = body.getStr("content");
        if (StrUtil.isBlank(contentStr)) return;
        try {
            JSONObject content = JSONUtil.parseObj(contentStr);
            Integer light = content.getInt("light");
            Boolean lamp = content.getBool("lamp");
            deviceService.onTelemetry(deviceId, light, lamp, eventTime);
        } catch (Exception ignore) {
            log.debug("message payload 非 JSON: {}", contentStr);
        }
    }

    private void handleStatus(String deviceId, JSONObject body) {
        String status = body.getStr("status", "");
        try {
            if ("ONLINE".equalsIgnoreCase(status)) {
                // 触发一次 telemetry 写入会自动生成"通信恢复"
                deviceService.onTelemetry(deviceId, null, null, new Date());
            } else if ("OFFLINE".equalsIgnoreCase(status)) {
                deviceDao.markOffline(deviceId);
            }
        } catch (Exception e) {
            log.error("处理 status 失败", e);
        }
    }

    private void handleCommandFeedback(String deviceId, JSONObject body) {
        // 异步命令的执行结果，0=成功
        Integer resultCode = body.getInt("result_code");
        String action = "turn_on"; // 默认值；body 一般不返回 action，靠最近一次命令上下文也行
        Object paras = body.get("paras");
        if (paras instanceof JSONObject) {
            String led = ((JSONObject) paras).getStr("Led", "");
            if ("OFF".equalsIgnoreCase(led)) action = "turn_off";
        }
        deviceService.onCommandFeedback(deviceId, resultCode != null && resultCode == 0, action);
    }

    private Date parseTime(String s) {
        Date d = Times.fromIso(s);
        return d;
    }

    private void recordPush(String resource, String event, String huaweiDeviceId, String deviceId) {
        long now = System.currentTimeMillis();
        pushCount.incrementAndGet();
        lastPushAt = now;
        lastResource = resource;
        lastEvent = event;
        lastHuaweiDeviceId = huaweiDeviceId;
        lastDeviceId = deviceId;
        lastPushAtByDevice.put(deviceId, now);
        lastResourceByDevice.put(deviceId, resource);
    }

    private Object ok() {
        return new JSONObject().set("ok", true);
    }

    @Autowired private HuaweiIamTokenHolder tokenHolder;

    /** 调试接口: 查看华为云是否持续推送到后端。 */
    @GetMapping("/push/status")
    public Object getPushStatus() {
        long now = System.currentTimeMillis();
        JSONObject devices = new JSONObject();
        for (Map.Entry<String, Long> e : lastPushAtByDevice.entrySet()) {
            long ts = e.getValue();
            devices.set(e.getKey(), new JSONObject()
                    .set("lastPushAt", Times.toIso(new Date(ts)))
                    .set("secondsSinceLastPush", (now - ts) / 1000)
                    .set("lastResource", lastResourceByDevice.get(e.getKey())));
        }
        return new JSONObject()
                .set("totalPushCount", pushCount.get())
                .set("lastPushAt", lastPushAt == 0 ? null : Times.toIso(new Date(lastPushAt)))
                .set("secondsSinceLastPush", lastPushAt == 0 ? null : (now - lastPushAt) / 1000)
                .set("lastResource", lastResource)
                .set("lastEvent", lastEvent)
                .set("lastHuaweiDeviceId", lastHuaweiDeviceId)
                .set("lastDeviceId", lastDeviceId)
                .set("lastHandleCostMs", lastHandleCostMs)
                .set("devices", devices);
    }

    /** 测试接口: 查看 IAM Token 状态 */
    @GetMapping("/token/status")
    public Object getTokenStatus() {
        JSONObject result = new JSONObject();
        result.set("configured", tokenHolder.isConfigured());
        if (tokenHolder.isConfigured()) {
            String token = tokenHolder.getToken();
            result.set("hasToken", token != null && !token.isEmpty());
            if (token != null && token.length() > 20) {
                result.set("tokenPreview", token.substring(0, 20) + "...");
            }
        } else {
            result.set("message", "IAM 配置未完成,请检查 application.yml 中的 huawei.iot.iam 配置");
        }
        return result;
    }
}
