package com.group8.streetlight.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Entity;
import cn.hutool.json.JSONObject;
import com.group8.streetlight.dao.UserDao;
import com.group8.streetlight.model.ApiError;
import com.group8.streetlight.util.JwtUtil;
import com.group8.streetlight.util.Passwords;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.Map;

/** 接口规范 §3.1 */
@Slf4j
@RestController
@RequestMapping("/api")
public class AuthController {

    @Autowired private UserDao userDao;
    @Autowired private JwtUtil jwt;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) throws SQLException {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        if (StrUtil.hasBlank(username, password)) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "用户名/密码必填"));
        }
        Entity u = userDao.findByUsername(username);
        if (u == null || !Passwords.match(password, u.getStr("password_hash"))) {
            return ResponseEntity.status(401).body(new ApiError("UNAUTHORIZED", "用户名或密码错误"));
        }
        // role 由数据库记录决定，登录时不接受前端传入的 role 参数
        String dbRole = u.getStr("role");
        String token = jwt.issue(username, dbRole);
        return ResponseEntity.ok(new JSONObject()
                .set("token", token)
                .set("role", dbRole)
                .set("username", username));
    }
}
