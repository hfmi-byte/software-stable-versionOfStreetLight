package com.group8.streetlight.task;

import cn.hutool.db.Entity;
import com.group8.streetlight.config.HutoolDb;
import com.group8.streetlight.config.StreetlightProperties;
import com.group8.streetlight.dao.AlarmDao;
import com.group8.streetlight.dao.DeviceDao;
import com.group8.streetlight.dao.EventDao;
import com.group8.streetlight.util.Times;
import com.group8.streetlight.ws.WsServer;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * 离线检测（接口规范 §6.2）。
 * 每 N 毫秒扫描一次，超过 last_seen 阈值则标记离线 + 生成"设备离线"告警。
 */
@Slf4j
@Component
public class OfflineScanner {

    @Autowired private DeviceDao deviceDao;
    @Autowired private AlarmDao alarmDao;
    @Autowired private EventDao eventDao;
    @Autowired private WsServer ws;
    @Autowired private StreetlightProperties props;

    @Value("${streetlight.offline-timeout-sec:60}")
    private int timeoutSec;

    @Scheduled(fixedDelayString = "${streetlight.offline-scan-interval-ms:30000}")
    public void scan() {
        try {
            List<Entity> rows = HutoolDb.use().query(
                    "SELECT d.id AS id, s.online AS online, s.last_seen AS last_seen " +
                    "FROM devices d LEFT JOIN device_status s ON d.id = s.device_id");
            long cutoff = System.currentTimeMillis() - timeoutSec * 1000L;
            for (Entity e : rows) {
                Integer online = e.getInt("online");
                Date lastSeen = e.getDate("last_seen");
                if (online != null && online == 1) {
                    if (lastSeen == null || lastSeen.getTime() < cutoff) {
                        String id = e.getStr("id");
                        deviceDao.markOffline(id);
                        Date now = new Date();
                        long aid = alarmDao.insert(id, now, "设备离线",
                                "设备超过 " + timeoutSec + " 秒未上报心跳", "中");
                        eventDao.insert(now, id + " 触发离线告警",
                                "后端检测到设备超过 " + timeoutSec + " 秒未上报。");
                        JSONObject ap = new JSONObject()
                                .set("id", String.valueOf(aid))
                                .set("device", id)
                                .set("time", Times.toIso(now))
                                .set("type", "设备离线")
                                .set("content", "设备超过 " + timeoutSec + " 秒未上报心跳")
                                .set("level", "中")
                                .set("status", "未处理");
                        ws.broadcast("alarm-new", ap);
                        // 推一份 device-update，让前端及时变红
                        ws.broadcast("device-update", deviceDao.findById(id));
                        log.info("[offline] {} 标记离线", id);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("离线扫描失败", e);
        }
    }
}
