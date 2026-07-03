package com.group8.streetlight.dao;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Entity;
import com.group8.streetlight.config.HutoolDb;
import com.group8.streetlight.model.Alarm;
import com.group8.streetlight.util.Times;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Repository
public class AlarmDao {

    public long insert(String deviceId, Date time, String type, String content, String level) throws SQLException {
        Entity e = Entity.create("alarms")
                .set("device_id", deviceId)
                .set("time", time)
                .set("type", type)
                .set("content", content)
                .set("level", level)
                .set("status", "未处理");
        return HutoolDb.use().insertForGeneratedKey(e);
    }

    public List<Alarm> find(String status, String device, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id, device_id, time, type, content, level, status FROM alarms WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (StrUtil.isNotBlank(status)) { sql.append(" AND status = ?"); params.add(status); }
        if (StrUtil.isNotBlank(device)) { sql.append(" AND device_id = ?"); params.add(device); }
        sql.append(" ORDER BY time DESC LIMIT ").append(Math.max(1, Math.min(limit, 500)));
        List<Entity> rows = HutoolDb.use().query(sql.toString(), params.toArray());
        List<Alarm> out = new ArrayList<>(rows.size());
        for (Entity e : rows) {
            Alarm a = new Alarm();
            a.setId(String.valueOf(e.getLong("id")));
            a.setDevice(e.getStr("device_id"));
            a.setTime(Times.toIso(e.getDate("time")));
            a.setType(e.getStr("type"));
            a.setContent(e.getStr("content"));
            a.setLevel(e.getStr("level"));
            a.setStatus(e.getStr("status"));
            out.add(a);
        }
        return out;
    }

    public Alarm findById(long id) throws SQLException {
        Entity e = HutoolDb.use().queryOne(
                "SELECT id, device_id, time, type, content, level, status FROM alarms WHERE id = ?", id);
        if (e == null) return null;
        Alarm a = new Alarm();
        a.setId(String.valueOf(e.getLong("id")));
        a.setDevice(e.getStr("device_id"));
        a.setTime(Times.toIso(e.getDate("time")));
        a.setType(e.getStr("type"));
        a.setContent(e.getStr("content"));
        a.setLevel(e.getStr("level"));
        a.setStatus(e.getStr("status"));
        return a;
    }

    public int resolve(long id) throws SQLException {
        return HutoolDb.use().execute("UPDATE alarms SET status = '已处理' WHERE id = ?", id);
    }
}
