package com.group8.streetlight.dao;

import cn.hutool.db.Entity;
import com.group8.streetlight.config.HutoolDb;
import com.group8.streetlight.model.Device;
import com.group8.streetlight.util.Times;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class DeviceDao {

    private static final String SELECT_JOIN =
            "SELECT d.id, d.name, d.location, d.threshold, d.auto_mode, d.huawei_device_id, " +
            "       s.online, s.lamp, s.light, s.last_seen " +
            "FROM devices d LEFT JOIN device_status s ON d.id = s.device_id";

    public List<Device> findAll() throws SQLException {
        List<Entity> rows = HutoolDb.use().query(SELECT_JOIN + " ORDER BY d.id");
        List<Device> out = new ArrayList<>(rows.size());
        for (Entity e : rows) out.add(toDevice(e));
        return out;
    }

    public Device findById(String id) throws SQLException {
        Entity e = HutoolDb.use().queryOne(SELECT_JOIN + " WHERE d.id = ?", id);
        return e == null ? null : toDevice(e);
    }

    public Device findByHuaweiDeviceId(String huaweiDeviceId) throws SQLException {
        Entity e = HutoolDb.use().queryOne(SELECT_JOIN + " WHERE d.huawei_device_id = ?", huaweiDeviceId);
        return e == null ? null : toDevice(e);
    }

    public boolean exists(String id) throws SQLException {
        Entity e = HutoolDb.use().queryOne("SELECT id FROM devices WHERE id = ?", id);
        return e != null;
    }

    public void insertDevice(String id, String name, String location,
                             int threshold, boolean autoMode, String huaweiDeviceId) throws SQLException {
        HutoolDb.use().insert(Entity.create("devices")
                .set("id", id)
                .set("name", name)
                .set("location", location)
                .set("threshold", threshold)
                .set("auto_mode", autoMode ? 1 : 0)
                .set("huawei_device_id", huaweiDeviceId));
        HutoolDb.use().insert(Entity.create("device_status")
                .set("device_id", id)
                .set("online", 0)
                .set("lamp", 0)
                .set("light", 0));
    }

    public int deleteDevice(String id) throws SQLException {
        return HutoolDb.use().execute("DELETE FROM devices WHERE id = ?", id);
    }

    public int updatePolicy(String id, int threshold, boolean autoMode) throws SQLException {
        return HutoolDb.use().execute(
                "UPDATE devices SET threshold = ?, auto_mode = ? WHERE id = ?",
                threshold, autoMode ? 1 : 0, id);
    }

    public void upsertStatus(String id, boolean online, Boolean lamp, Integer light, java.util.Date lastSeen) throws SQLException {
        // 使用 ON DUPLICATE KEY UPDATE
        StringBuilder sb = new StringBuilder(
                "INSERT INTO device_status (device_id, online, lamp, light, last_seen) VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE online = VALUES(online)");
        List<Object> params = new ArrayList<>();
        params.add(id);
        params.add(online ? 1 : 0);
        params.add(lamp == null ? 0 : (lamp ? 1 : 0));
        params.add(light == null ? 0 : light);
        params.add(lastSeen);
        if (lamp != null) sb.append(", lamp = VALUES(lamp)");
        if (light != null) sb.append(", light = VALUES(light)");
        if (lastSeen != null) sb.append(", last_seen = VALUES(last_seen)");
        HutoolDb.use().execute(sb.toString(), params.toArray());
    }

    public int markOffline(String id) throws SQLException {
        return HutoolDb.use().execute("UPDATE device_status SET online = 0 WHERE device_id = ?", id);
    }

    private Device toDevice(Entity e) {
        Device d = new Device();
        d.setId(e.getStr("id"));
        d.setName(e.getStr("name"));
        d.setLocation(e.getStr("location"));
        d.setThreshold(e.getInt("threshold"));
        d.setAutoMode(intToBool(e.getInt("auto_mode")));
        d.setHuaweiDeviceId(e.getStr("huawei_device_id"));
        Integer online = e.getInt("online");
        d.setOnline(intToBool(online));
        Integer lamp = e.getInt("lamp");
        d.setLamp(intToBool(lamp));
        Integer light = e.getInt("light");
        d.setLight(light == null ? 0 : light);
        d.setLastSeen(Times.toIso(e.getDate("last_seen")));
        return d;
    }

    private Boolean intToBool(Integer i) { return i != null && i == 1; }
}
