package com.group8.streetlight.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.group8.streetlight.dao.HistoryDao;
import com.group8.streetlight.model.ApiError;
import com.group8.streetlight.model.Device;
import com.group8.streetlight.model.HistoryPoint;
import com.group8.streetlight.service.DeviceService;
import com.group8.streetlight.util.Times;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** 接口规范 §3.2 - §3.4 */
@Slf4j
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    @Autowired private DeviceService deviceService;
    @Autowired private HistoryDao historyDao;

    @GetMapping
    public ResponseEntity<?> list() throws SQLException {
        return ResponseEntity.ok(deviceService.all());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable("id") String id) throws SQLException {
        Device d = deviceService.get(id);
        if (d == null) return ResponseEntity.status(404).body(new ApiError("DEVICE_NOT_FOUND", "设备不存在"));
        return ResponseEntity.ok(d);
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody Map<String, Object> body) throws SQLException {
        String id       = (String) body.get("id");
        String name     = (String) body.get("name");
        String location = (String) body.get("location");
        String huawei   = (String) body.get("huaweiDeviceId");
        if (StrUtil.hasBlank(id, name)) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "id / name 必填"));
        }
        try {
            Device d = deviceService.add(id, name, location, huawei);
            return ResponseEntity.status(201).body(d);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(new ApiError(e.getMessage(), "设备 id 已存在"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> remove(@PathVariable("id") String id) throws SQLException {
        boolean ok = deviceService.remove(id);
        if (!ok) return ResponseEntity.status(404).body(new ApiError("DEVICE_NOT_FOUND", "设备不存在"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<?> history(@PathVariable("id") String id,
                                     @RequestParam(value = "from", required = false) String from,
                                     @RequestParam(value = "to", required = false) String to,
                                     @RequestParam(value = "limit", defaultValue = "100") int limit) throws SQLException {
        Device d = deviceService.get(id);
        if (d == null) return ResponseEntity.status(404).body(new ApiError("DEVICE_NOT_FOUND", "设备不存在"));
        List<HistoryPoint> rows = historyDao.find(id, Times.fromIso(from), Times.fromIso(to), limit);
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/{id}/cmd")
    public ResponseEntity<?> sendCmd(@PathVariable("id") String id,
                                     @RequestBody Map<String, Object> body) throws SQLException {
        String action = (String) body.get("action");
        if (!"turn_on".equalsIgnoreCase(action) && !"turn_off".equalsIgnoreCase(action)) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_ACTION", "action 必须是 turn_on / turn_off"));
        }
        try {
            Device d = deviceService.manualCommand(id, action);
            if (d == null) return ResponseEntity.status(404).body(new ApiError("DEVICE_NOT_FOUND", "设备不存在"));
            return ResponseEntity.ok(new JSONObject().set("ok", true).set("device", d));
        } catch (IllegalStateException e) {
            String code = e.getMessage();
            if ("DEVICE_OFFLINE".equals(code)) {
                return ResponseEntity.status(409).body(new ApiError(code, "设备离线，指令未送达"));
            }
            // COMMAND_SEND_FAILED：后端收到但华为云下发失败
            return ResponseEntity.status(502).body(new ApiError(code, "指令发送至华为云失败，请稍后重试"));
        }
    }

    @PutMapping("/{id}/policy")
    public ResponseEntity<?> savePolicy(@PathVariable("id") String id,
                                        @RequestBody Map<String, Object> body) throws SQLException {
        Object thr = body.get("threshold");
        Object am  = body.get("autoMode");
        if (thr == null || am == null) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "threshold / autoMode 必填"));
        }
        int threshold;
        try { threshold = thr instanceof Number ? ((Number) thr).intValue() : Integer.parseInt(thr.toString()); }
        catch (Exception e) { return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "threshold 必须是整数")); }
        boolean autoMode = am instanceof Boolean ? (Boolean) am : Boolean.parseBoolean(am.toString());
        try {
            Device d = deviceService.savePolicy(id, threshold, autoMode);
            if (d == null) return ResponseEntity.status(404).body(new ApiError("DEVICE_NOT_FOUND", "设备不存在"));
            return ResponseEntity.ok(new JSONObject()
                    .set("ok", true)
                    .set("threshold", d.getThreshold())
                    .set("autoMode", d.getAutoMode()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage(), "threshold 超出 50-1000 lux 范围"));
        }
    }
}
