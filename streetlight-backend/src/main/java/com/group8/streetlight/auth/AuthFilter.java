package com.group8.streetlight.auth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.group8.streetlight.model.ApiError;
import com.group8.streetlight.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 鉴权过滤器（接口规范 §1.2）：
 *  - 所有 /api/** 都需要 token，除了 /api/login
 *  - admin 才能调 POST /api/devices 和 DELETE /api/devices/:id（§6.3）
 *  - WebSocket /ws 的鉴权在 WsHandshakeInterceptor 里做
 */
@Slf4j
@Component
@Order(1)
public class AuthFilter implements Filter {

    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/login",
            "/api/huawei/push",       // Huawei IoT 数据转发 push（用单独的鉴权方式）
            "/error"
    );

    @Autowired
    private JwtUtil jwt;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        String path = request.getRequestURI();
        String method = request.getMethod();

        // 放行非 /api 路径（比如 /ws 由 WebSocket 自己处理）
        if (!path.startsWith("/api")) { chain.doFilter(req, resp); return; }
        // CORS 预检放行
        if ("OPTIONS".equalsIgnoreCase(method)) { chain.doFilter(req, resp); return; }

        for (String p : PUBLIC_PATHS) {
            if (path.equals(p) || path.startsWith(p + "/")) {
                chain.doFilter(req, resp);
                return;
            }
        }

        String authz = request.getHeader("Authorization");
        if (StrUtil.isBlank(authz) || !authz.startsWith("Bearer ")) {
            writeError(response, 401, "UNAUTHORIZED", "缺少 Authorization 头");
            return;
        }
        String token = authz.substring("Bearer ".length()).trim();
        JSONObject payload = jwt.verify(token);
        if (payload == null) {
            writeError(response, 401, "UNAUTHORIZED", "token 无效或已过期");
            return;
        }

        String role = payload.getStr("role", "municipal");
        String username = payload.getStr("sub");
        request.setAttribute("auth.role", role);
        request.setAttribute("auth.username", username);

        // admin 专属接口（§6.3）
        if (!"admin".equalsIgnoreCase(role)) {
            if (path.equals("/api/devices") && "POST".equalsIgnoreCase(method)) {
                writeError(response, 403, "FORBIDDEN", "仅 admin 可新增设备"); return;
            }
            if (path.matches("/api/devices/[^/]+") && "DELETE".equalsIgnoreCase(method)) {
                writeError(response, 403, "FORBIDDEN", "仅 admin 可删除设备"); return;
            }
        }

        chain.doFilter(req, resp);
    }

    private void writeError(HttpServletResponse resp, int status, String code, String msg) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=utf-8");
        // 过滤器层直接写响应时 Spring MVC 的 CorsConfig 不会介入，需手动加跨域头
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Headers", "*");
        resp.getWriter().write(JSONUtil.toJsonStr(new ApiError(code, msg)));
    }
}
