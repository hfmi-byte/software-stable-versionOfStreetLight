package com.group8.streetlight.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.group8.streetlight.dao.DeviceDao;
import com.group8.streetlight.model.Device;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.Map;

/**
 * 接口规范 §3.7：转发给 RAG 智能体。
 * 此处给一个**带上下文的占位**实现，后续直接换成对接 MaxKB 或其他 RAG 服务。
 *
 * 真接 MaxKB 时把下面的 echo 逻辑替换为 HTTP 调 MaxKB 的对话接口即可。
 */
@Slf4j
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    @Autowired private DeviceDao deviceDao;

    @PostMapping
    public ResponseEntity<?> ask(@RequestBody Map<String, Object> body) throws SQLException {
        String query    = (String) body.get("query");
        String deviceId = (String) body.get("deviceId");
        if (StrUtil.isBlank(query)) {
            return ResponseEntity.ok(new JSONObject().set("answer", "请输入问题"));
        }
        StringBuilder ans = new StringBuilder();
        ans.append("[占位回答] 收到问题：\"").append(query).append("\"。");
        if (StrUtil.isNotBlank(deviceId)) {
            Device d = deviceDao.findById(deviceId);
            if (d != null) {
                ans.append("\n当前设备 ").append(deviceId)
                   .append(" 状态：online=").append(Boolean.TRUE.equals(d.getOnline()) ? "是" : "否")
                   .append("，lamp=").append(Boolean.TRUE.equals(d.getLamp()) ? "开" : "关")
                   .append("，light=").append(d.getLight()).append(" lux，threshold=")
                   .append(d.getThreshold());
            }
        }
        ans.append("\n（接入 MaxKB / RAG 后会返回真实答案）");
        return ResponseEntity.ok(new JSONObject().set("answer", ans.toString()));
    }
}
