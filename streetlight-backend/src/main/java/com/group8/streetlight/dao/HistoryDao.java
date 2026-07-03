package com.group8.streetlight.dao;

import cn.hutool.db.Entity;
import com.group8.streetlight.config.HutoolDb;
import com.group8.streetlight.model.HistoryPoint;
import com.group8.streetlight.util.Times;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Repository
public class HistoryDao {

    public void insert(String deviceId, Date time, int light, boolean lamp, String source) throws SQLException {
        HutoolDb.use().insert(Entity.create("history")
                .set("device_id", deviceId)
                .set("time", time)
                .set("light", light)
                .set("lamp", lamp ? 1 : 0)
                .set("source", source));
    }

    public List<HistoryPoint> find(String deviceId, Date from, Date to, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT device_id, time, light, lamp, source FROM history WHERE device_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(deviceId);
        if (from != null) { sql.append(" AND time >= ?"); params.add(from); }
        if (to != null)   { sql.append(" AND time <= ?"); params.add(to); }
        // DESC 取最新 N 条，再逆序还原为时间升序，确保图表 X 轴左旧右新
        sql.append(" ORDER BY time DESC LIMIT ").append(Math.max(1, Math.min(limit, 1000)));

        List<Entity> rows = HutoolDb.use().query(sql.toString(), params.toArray());
        List<HistoryPoint> out = new ArrayList<>(rows.size());
        for (Entity e : rows) {
            out.add(new HistoryPoint(
                    Times.toIso(e.getDate("time")),
                    e.getStr("device_id"),
                    e.getInt("light"),
                    e.getInt("lamp") == 1,
                    e.getStr("source")
            ));
        }
        Collections.reverse(out);  // 还原为 ASC 顺序
        return out;
    }
}
