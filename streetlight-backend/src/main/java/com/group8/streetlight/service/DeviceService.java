package com.group8.streetlight.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.group8.streetlight.config.StreetlightProperties;
import com.group8.streetlight.dao.AlarmDao;
import com.group8.streetlight.dao.DeviceDao;
import com.group8.streetlight.dao.EventDao;
import com.group8.streetlight.dao.HistoryDao;
import com.group8.streetlight.model.Device;
import com.group8.streetlight.util.Times;
import com.group8.streetlight.ws.WsServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 路灯业务核心：
 *  - 收到设备上报：写实时状态 + 写历史 + 跑自动联动 + 推 WebSocket
 *  - 离线检测：扫描 last_seen，超过阈值则下线 + 生成告警
 *  - 命令下发后回填本地状态、广播 lamp-feedback
 */
@Slf4j
@Service
public class DeviceService {

    @Autowired private DeviceDao deviceDao;
    @Autowired private HistoryDao historyDao;
    @Autowired private AlarmDao alarmDao;
    @Autowired private EventDao eventDao;
    @Autowired private WsServer ws;
    @Autowired private CommandSender commandSender;
    @Autowired private StreetlightProperties props;

    private final Map<String, Long> lastAutoCommandAt = new ConcurrentHashMap<>();
    private final Map<String, Object> autoLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> lastHistoryAt = new ConcurrentHashMap<>();
    private final ExecutorService autoCommandExecutor = Executors.newFixedThreadPool(3, new ThreadFactory() {
        private int idx = 0;
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "streetlight-auto-cmd-" + (++idx));
            t.setDaemon(true);
            return t;
        }
    });

    /**
     * 处理一次属性上报（光照 + 灯状态）。
     * 来源是 huawei push 或 emqx 订阅，统一汇到这里。
     *
     * @param deviceId 前端用 ID（必须先存在于 devices 表）
     * @param light    光照值 lux；可为 null 表示本次只更新灯状态
     * @param lamp     灯当前状态；可为 null 表示本次只上报光照
     * @param eventTime 设备上报时间；为空用服务端当前时间
     */
    public void onTelemetry(String deviceId, Integer light, Boolean lamp, Date eventTime) {
        if (StrUtil.isBlank(deviceId)) return;
        Date receivedAt = new Date();
        Date sampleTime = eventTime != null ? eventTime : receivedAt;
        try {
            Device d = deviceDao.findById(deviceId);
            if (d == null) {
                log.warn("[telemetry] 未注册设备 {} 上报数据，忽略（请先在 devices 表登记）", deviceId);
                return;
            }
            boolean wasOnline = Boolean.TRUE.equals(d.getOnline());
            deviceDao.upsertStatus(deviceId, true, lamp, light, receivedAt);

            if (light != null && shouldWriteHistory(deviceId, receivedAt.getTime())) {
                historyDao.insert(deviceId, sampleTime, light, lamp != null ? lamp : Boolean.TRUE.equals(d.getLamp()), "MQTT上报");
            }

            // 通信恢复告警（从离线 → 在线）
            if (!wasOnline) {
                long aid = alarmDao.insert(deviceId, receivedAt, "通信恢复", "设备重新上线并恢复数据上报", "低");
                pushAlarm(aid, deviceId, receivedAt, "通信恢复", "设备重新上线并恢复数据上报", "低");
                eventDao.insert(receivedAt, deviceId + " 通信恢复", "设备重新上线");
            }

            // 推 device-update
            Device updated = deviceDao.findById(deviceId);
            ws.broadcast("device-update", updated);

            handleAutoMode(updated, light, receivedAt);
        } catch (SQLException e) {
            log.error("处理上报失败 deviceId={}", deviceId, e);
        }
    }

    private boolean shouldWriteHistory(String deviceId, long now) {
        long intervalMs = Math.max(0, props.getHistorySampleIntervalSec()) * 1000L;
        if (intervalMs == 0) {
            return true;
        }
        Long last = lastHistoryAt.get(deviceId);
        if (last != null && now - last < intervalMs) {
            return false;
        }
        lastHistoryAt.put(deviceId, now);
        return true;
    }

    private void handleAutoMode(Device updated, Integer light, Date receivedAt) throws SQLException {
        if (!Boolean.TRUE.equals(updated.getAutoMode())
                || light == null
                || !Boolean.TRUE.equals(updated.getOnline())) {
            return;
        }
        synchronized (autoLock(updated.getId())) {
            boolean curLamp = Boolean.TRUE.equals(updated.getLamp());
            Boolean targetLamp = decideTargetLamp(light, updated.getThreshold(), curLamp);
            if (targetLamp == null || curLamp == targetLamp) {
                return;
            }

            boolean shouldBeOn = targetLamp;
            String action = shouldBeOn ? "turn_on" : "turn_off";
            long now = System.currentTimeMillis();
            long cooldownMs = Math.max(0, props.getAutoCommandCooldownSec()) * 1000L;
            // 设备级综合冷却：turn_on 和 turn_off 共用同一个槽，防止短时间内来回反转指令
            String cooldownKey = updated.getId() + ":any";
            Long last = lastAutoCommandAt.get(cooldownKey);
            if (last != null && now - last < cooldownMs) {
                long remain = (cooldownMs - (now - last) + 999) / 1000;
                log.debug("[auto] {} 光照 {} 阈值 {}，处于冷却期，{} 秒后可再次下发",
                        updated.getId(), light, updated.getThreshold(), remain);
                return;
            }
            lastAutoCommandAt.put(cooldownKey, now);

            String type = shouldBeOn ? "自动开灯" : "自动关灯";
            String relation = shouldBeOn ? "低于" : "高于或等于";
            int threshold = shouldBeOn
                    ? updated.getThreshold()
                    : updated.getThreshold() + Math.max(0, props.getAutoOffHysteresisLux());
            String content = "光照值 " + light + " " + relation + "阈值 " + threshold;
            log.info("[auto] {} 光照 {} 阈值 {} → 后台下发 {}", updated.getId(), light, updated.getThreshold(), action);

            long aid = alarmDao.insert(updated.getId(), receivedAt, type, content, "低");
            pushAlarm(aid, updated.getId(), receivedAt, type, content, "低");
            eventDao.insert(receivedAt, type + "指令", updated.getId() + " " + content + "，系统准备发布 " + action);

            autoCommandExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    sendAutoCommand(updated.getId(), updated.getHuaweiDeviceId(), action, shouldBeOn);
                }
            });
        }
    }

    private Object autoLock(String deviceId) {
        return autoLocks.computeIfAbsent(deviceId, k -> new Object());
    }

    private Boolean decideTargetLamp(int light, int onThreshold, boolean curLamp) {
        int offThreshold = onThreshold + Math.max(0, props.getAutoOffHysteresisLux());
        if (light < onThreshold) {
            return true;
        }
        if (light >= offThreshold) {
            return false;
        }
        return curLamp;
    }

    private void sendAutoCommand(String deviceId, String huaweiDeviceId, String action, boolean targetLamp) {
        boolean ok = commandSender.send(deviceId, huaweiDeviceId, action);
        Date now = new Date();
        try {
            eventDao.insert(now, "自动联动结果 " + deviceId,
                    "系统发布 " + action + (ok ? " 成功" : " 失败"));
            if (ok) {
                deviceDao.upsertStatus(deviceId, true, targetLamp, null, now);
                Device updated = deviceDao.findById(deviceId);
                if (updated != null) ws.broadcast("device-update", updated);
            } else {
                // 失败时保持冷却，不提前重置，避免向设备轰炸重复命令
                log.warn("[auto] {} 下发 {} 失败，冷却期内不重试", deviceId, action);
            }
        } catch (SQLException e) {
            log.error("[auto] 记录自动联动结果失败 deviceId={}", deviceId, e);
        }
    }

    /** 设备执行命令后返回的反馈，刷新本地灯状态 + 广播 lamp-feedback。 */
    public synchronized void onCommandFeedback(String deviceId, boolean success, String action) {
        if (StrUtil.isBlank(deviceId) || StrUtil.isBlank(action)) return;
        try {
            if (success) {
                boolean lamp = "turn_on".equalsIgnoreCase(action);
                deviceDao.upsertStatus(deviceId, true, lamp, null, new Date());
            }
            JSONObject payload = new JSONObject()
                    .set("deviceId", deviceId)
                    .set("lamp", "turn_on".equalsIgnoreCase(action))
                    .set("success", success);
            ws.broadcast("lamp-feedback", payload);
            Device d = deviceDao.findById(deviceId);
            if (d != null) ws.broadcast("device-update", d);
        } catch (SQLException e) {
            log.error("处理反馈失败 deviceId={}", deviceId, e);
        }
    }

    public List<Device> all() throws SQLException { return deviceDao.findAll(); }
    public Device get(String id) throws SQLException { return deviceDao.findById(id); }

    public Device add(String id, String name, String location, String huaweiDeviceId) throws SQLException {
        if (deviceDao.exists(id)) throw new IllegalArgumentException("DEVICE_EXISTS");
        deviceDao.insertDevice(id, name, location, 150, true, huaweiDeviceId);
        return deviceDao.findById(id);
    }

    public boolean remove(String id) throws SQLException {
        if (!deviceDao.exists(id)) return false;
        deviceDao.deleteDevice(id);
        return true;
    }

    public Device savePolicy(String id, int threshold, boolean autoMode) throws SQLException {
        if (threshold < 50 || threshold > 1000) throw new IllegalArgumentException("THRESHOLD_OUT_OF_RANGE");
        if (deviceDao.updatePolicy(id, threshold, autoMode) == 0) return null;
        return deviceDao.findById(id);
    }

    /** 手动下发开关灯，并写日志/事件。 */
    public Device manualCommand(String id, String action) throws SQLException {
        Device d = deviceDao.findById(id);
        if (d == null) return null;
        if (!Boolean.TRUE.equals(d.getOnline())) {
            throw new IllegalStateException("DEVICE_OFFLINE");
        }
        boolean ok = commandSender.send(id, d.getHuaweiDeviceId(), action);
        Date now = new Date();
        boolean lamp = "turn_on".equalsIgnoreCase(action);
        eventDao.insert(now, "手动控制 " + id, "管理员下发 " + action + (ok ? "" : "（发送失败）"));
        if (!ok) {
            throw new IllegalStateException("COMMAND_SEND_FAILED");
        }
        deviceDao.upsertStatus(id, true, lamp, null, now);
        Device updated = deviceDao.findById(id);
        ws.broadcast("device-update", updated);
        return updated;
    }

    private void pushAlarm(long id, String deviceId, Date time, String type, String content, String level) {
        JSONObject a = new JSONObject()
                .set("id", String.valueOf(id))
                .set("device", deviceId)
                .set("time", Times.toIso(time))
                .set("type", type)
                .set("content", content)
                .set("level", level)
                .set("status", "未处理");
        ws.broadcast("alarm-new", a);
    }

    @PreDestroy
    public void shutdown() {
        autoCommandExecutor.shutdownNow();
    }
}
