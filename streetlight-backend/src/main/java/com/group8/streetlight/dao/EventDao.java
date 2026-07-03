package com.group8.streetlight.dao;

import cn.hutool.db.Entity;
import com.group8.streetlight.config.HutoolDb;
import com.group8.streetlight.model.Event;
import com.group8.streetlight.util.Times;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Repository
public class EventDao {

    public void insert(Date time, String title, String copy) throws SQLException {
        HutoolDb.use().insert(Entity.create("events")
                .set("time", time)
                .set("title", title)
                .set("copy", copy));
    }

    public List<Event> latest(int limit) throws SQLException {
        List<Entity> rows = HutoolDb.use().query(
                "SELECT time, title, copy FROM events ORDER BY time DESC LIMIT " + Math.max(1, Math.min(limit, 200)));
        List<Event> out = new ArrayList<>(rows.size());
        for (Entity e : rows) {
            out.add(new Event(Times.toIso(e.getDate("time")), e.getStr("title"), e.getStr("copy")));
        }
        return out;
    }
}
