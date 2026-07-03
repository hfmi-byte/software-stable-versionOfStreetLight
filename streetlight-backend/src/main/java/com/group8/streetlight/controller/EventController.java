package com.group8.streetlight.controller;

import com.group8.streetlight.dao.EventDao;
import com.group8.streetlight.model.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

/** 接口规范 §3.6 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired private EventDao eventDao;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(value = "limit", defaultValue = "10") int limit) throws SQLException {
        List<Event> rows = eventDao.latest(limit);
        return ResponseEntity.ok(rows);
    }
}
