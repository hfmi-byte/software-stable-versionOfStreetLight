package com.group8.streetlight.controller;

import com.group8.streetlight.dao.AlarmDao;
import com.group8.streetlight.model.Alarm;
import com.group8.streetlight.model.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

/** 接口规范 §3.5 */
@Slf4j
@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    @Autowired private AlarmDao alarmDao;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(value = "status", required = false) String status,
                                  @RequestParam(value = "device", required = false) String device,
                                  @RequestParam(value = "limit", defaultValue = "50") int limit) throws SQLException {
        List<Alarm> rows = alarmDao.find(status, device, limit);
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolve(@PathVariable("id") String id) throws SQLException {
        long aid;
        try { aid = Long.parseLong(id); } catch (NumberFormatException e) {
            return ResponseEntity.status(404).body(new ApiError("ALARM_NOT_FOUND", "告警不存在"));
        }
        if (alarmDao.findById(aid) == null) {
            return ResponseEntity.status(404).body(new ApiError("ALARM_NOT_FOUND", "告警不存在"));
        }
        alarmDao.resolve(aid);
        return ResponseEntity.noContent().build();
    }
}
